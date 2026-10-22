(ns com.borba.be-boilerplate.system.event-relay
  "Publishes the events of the event store to Kafka: the event store is the
   outbox. A request stores the state of an aggregate and its event in one
   transaction and never calls Kafka, so it is neither slowed nor failed by
   Kafka, and an event that is stored is published, sooner or later.

     :service/event-relay
     {:database       #ig/ref :components/database
      :event-store    #ig/ref :components/event-store
      :kafka-producer #ig/ref :components/kafka-producer
      :relay-name     \"be-boilerplate\"
      :topics         {\"order\" \"order-events\"}}

   :topics says the topic of the events of each aggregate type; the events of
   a type that is not there are passed over. The relay reads the feed of the
   event store after the position it has published up to, publishes each event
   in order, and keeps the new position in the table event_relay_positions.

   Delivery is at-least-once: the position is committed after the events are
   on Kafka, so a relay that stops between the two publishes them again, and a
   consumer must be safe to receive an event twice. The events of an aggregate
   are published in order, one after the other, and a failure stops the batch
   at the event that failed, which is tried again on the next round.

   The row of the relay is also its lock: a round holds it, and an instance
   that finds it held skips the round, so of the instances of a service only
   one publishes at a time.

   An event is published once it is :settle-ms old by the clock of the
   database. A position is given when an event is inserted, not when its
   transaction commits, so a transaction that commits late can store an event
   behind one that was already read; waiting a moment lets it commit first."
  (:require
   [borba.event-store :as es]
   [borba.kafka-producer :as kafka]
   [borba.sql-client :as sql]
   [clojure.tools.logging :as log]
   [com.borba.be-boilerplate.events.producer :as producer]
   [integrant.core :as ig])
  (:import
   (java.time Instant)))

(set! *warn-on-reflection* true)

(def ^:private default-batch-size 100)

(def ^:private default-poll-interval-ms 500)

(def ^:private default-settle-ms 1000)

(def ^:private stop-timeout-ms 10000)

(def ^:private thread-name "be-boilerplate-event-relay")

(def ^:private register-sql
  (str "INSERT INTO event_relay_positions (name) VALUES (?)"
       " ON CONFLICT (name) DO NOTHING"))

(def ^:private claim-sql
  (str "SELECT position FROM event_relay_positions WHERE name = ?"
       " FOR UPDATE SKIP LOCKED"))

(def ^:private advance-sql
  (str "UPDATE event_relay_positions SET position = ?, updated_at = now()"
       " WHERE name = ?"))

(def ^:private database-now-sql "SELECT now() AS now")

;; A round

(defn settled
  "Returns the events, in order, up to the first one that is not older than
   the cutoff, which waits for the next round with the ones after it.
   - events: the events, in the order of their position
   - cutoff: the time an event must not be after"
  [events
   ^Instant cutoff]
  (take-while #(not (.isAfter ^Instant (:created-at %) cutoff)) events))

(defn publish-all!
  "Publishes the events in order, and stops at the first one that fails.
   Returns the :position of the last event that is past, published or passed
   over because its aggregate type has no topic, how many were :published, and
   the :failure that stopped it, if one did.
   - send!: a function of the topic, the key and the value that returns when
     the message is stored, and throws when it is not
   - topics: a map from each aggregate type to its topic
   - events: the events, in the order of their position
   - position: where the relay is before these events"
  [send!
   topics
   events
   position]
  (reduce (fn [progress event]
            (let [topic (get topics (:aggregate-type event))]
              (try
                (when topic
                  (send! topic
                         (:aggregate-id event)
                         (producer/event-message event)))
                (cond-> (assoc progress :position (:position event))
                  topic (update :published inc))
                (catch Exception cause
                  (reduced (assoc progress :failure cause))))))
          {:position  position
           :published 0}
          events))

(defn relay-once!
  "Runs one round: holds the row of the relay, publishes the settled events
   after its position, and moves the position past them, in one transaction.
   Returns what `publish-all!` returns, with how many events were :ready, or
   nil when another instance holds the row.
   - sql-client: the data source of the database
   - event-store: the event store
   - send!: what publishes a message, see `publish-all!`
   - relay-name: the name of the relay, the key of its row
   - topics: a map from each aggregate type to its topic
   - batch-size: the most events a round reads
   - settle-ms: how old an event must be to be published"
  [{:keys [sql-client event-store send! relay-name topics batch-size
           settle-ms]}]
  (sql/transact!
   sql-client
   (fn [tx]
     (when-let [claimed (sql/execute-one! tx [claim-sql relay-name])]
       (let [now      ^Instant (:now (sql/execute-one! tx [database-now-sql]))
             cutoff   (.minusMillis now (long settle-ms))
             events   (es/get-all-events (assoc event-store :datasource tx)
                                         {:after (:position claimed)
                                          :limit batch-size})
             ready    (settled events cutoff)
             progress (publish-all! send! topics ready (:position claimed))]
         (when (< (:position claimed) (:position progress))
           (sql/execute! tx [advance-sql (:position progress) relay-name]))
         (assoc progress :ready (count ready)))))))

;; The loop

(defn- sleep
  "Waits, and returns false when the thread was interrupted."
  [milliseconds]
  (try (Thread/sleep (long milliseconds))
       true
       (catch InterruptedException _
         false)))

(defn- run-round!
  "Runs a round, logging what it did, and returns whether the relay should
   go on at once, because it read a full batch and there may be more."
  [{:keys [relay-name batch-size] :as relay}]
  (try
    (let [{:keys [position published failure ready]} (relay-once! relay)]
      (when failure
        (log/warnf failure "event relay %s stopped at position %d: %s"
                   relay-name
                   position
                   (or (:error (kafka/error-data failure)) :failed)))
      (when (pos? (or published 0))
        (log/infof "event relay %s published %d event(s), up to position %d"
                   relay-name
                   published
                   position))
      (and (nil? failure) (= batch-size ready)))
    (catch Exception cause
      (log/error cause "event relay" relay-name "round failed")
      false)))

(defn- relay-loop!
  [running
   {:keys [poll-interval-ms] :as relay}]
  (while @running
    (when-not (run-round! relay)
      (when-not (sleep poll-interval-ms)
        (reset! running false)))))

;; The component

(defmethod ig/init-key :service/event-relay
  [_ {:keys [database event-store kafka-producer relay-name topics batch-size
             poll-interval-ms settle-ms]
      :or   {batch-size       default-batch-size
             poll-interval-ms default-poll-interval-ms
             settle-ms        default-settle-ms}}]
  (sql/execute! database [register-sql relay-name])
  (let [running (atom true)
        relay   {:sql-client       database
                 :event-store      event-store
                 :send!            #(kafka/send-sync! kafka-producer %1 %2 %3)
                 :relay-name       relay-name
                 :topics           topics
                 :batch-size       batch-size
                 :poll-interval-ms poll-interval-ms
                 :settle-ms        settle-ms}
        thread  (doto (Thread. ^Runnable #(relay-loop! running relay)
                               ^String thread-name)
                  (.setDaemon true)
                  (.start))]
    (log/infof "event relay %s publishes %s" relay-name topics)
    {:running    running
     :thread     thread
     :relay-name relay-name}))

(defmethod ig/halt-key! :service/event-relay
  [_ {:keys [running ^Thread thread relay-name]}]
  (reset! running false)
  (.join thread (long stop-timeout-ms))
  (if (.isAlive thread)
    (log/errorf "event relay %s did not stop within %d ms"
                relay-name
                stop-timeout-ms)
    (log/infof "event relay %s stopped" relay-name)))
