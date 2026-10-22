(ns ^:integration flows.event-relay-flow-test
  "Integration tests for the event relay against a real PostgreSQL: the
   events table of the event store and the positions of the relay
   (db/migrations/002_event_relay_positions.sql). Kafka is a recorder, so the
   pipeline needs no broker."
  (:require
   [borba.event-store :as es]
   [borba.sql-client :as sql]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [com.borba.be-boilerplate.system.event-relay :as relay]
   [flows.support :as support]))

(use-fixtures :once support/with-components)

(def ^:private register-sql
  "INSERT INTO event_relay_positions (name) VALUES (?)")

(def ^:private position-sql
  "SELECT position FROM event_relay_positions WHERE name = ?")

(def ^:private hold-sql
  "SELECT position FROM event_relay_positions WHERE name = ? FOR UPDATE")

(defn- fresh-relay
  "A relay of its own, for an aggregate type of its own, so the events of
   the other tests are passed over."
  [send!]
  (let [{:keys [sql-client event-store]} (support/components)
        relay-name                       (str "relay-it-" (random-uuid))]
    (sql/execute! sql-client [register-sql relay-name])
    {:sql-client  sql-client
     :event-store event-store
     :send!       send!
     :relay-name  relay-name
     :topics      {relay-name "relay-events"}
     :batch-size  10000
     :settle-ms   0}))

(defn- append-events!
  [{:keys [event-store relay-name]} n]
  (let [aggregate-id (str (random-uuid))]
    (doseq [version (range 1 (inc n))]
      (es/append! event-store {:aggregate-id   aggregate-id
                               :aggregate-type relay-name
                               :event-type     "order-created"
                               :payload        {:version version}
                               :version        version}))
    aggregate-id))

(defn- position-of
  [{:keys [sql-client relay-name]}]
  (:position (sql/execute-one! sql-client [position-sql relay-name])))

(defn- recorder
  [fail?]
  (let [sent (atom [])]
    {:sent  sent
     :send! (fn [topic message-key value]
              (when (fail? value)
                (throw (ex-info "broker away" {})))
              (swap! sent conj [topic message-key value]))}))

(deftest relays-in-order-test
  (testing "publishes the events in order and moves past them, once"
    (let [{:keys [sent send!]} (recorder (constantly false))
          relay-config         (fresh-relay send!)
          aggregate-id         (append-events! relay-config 3)]
      (is (= 3 (:published (relay/relay-once! relay-config))))
      (is (= [1 2 3] (map #(get-in % [2 :data :version]) @sent)))
      (is (every? #(= ["relay-events" aggregate-id] (take 2 %)) @sent))
      (is (pos? (position-of relay-config)))
      (is (= 0 (:published (relay/relay-once! relay-config))))
      (is (= 3 (count @sent))))))

(deftest failure-test
  (testing "a failure keeps the position before the event, which is tried
            again on the next round"
    (let [broken?              (atom true)
          second-event?        #(= 2 (get-in % [:data :version]))
          {:keys [sent send!]} (recorder #(and @broken? (second-event? %)))
          relay-config         (fresh-relay send!)]
      (append-events! relay-config 3)
      (let [first-round (relay/relay-once! relay-config)]
        (is (some? (:failure first-round)))
        (is (= 1 (:published first-round))))
      (reset! broken? false)
      (is (= 2 (:published (relay/relay-once! relay-config))))
      (is (= [1 2 3] (map #(get-in % [2 :data :version]) @sent))))))

(deftest settle-test
  (testing "an event younger than the settle time waits for the next round"
    (let [{:keys [sent send!]} (recorder (constantly false))
          relay-config         (assoc (fresh-relay send!) :settle-ms 60000)]
      (append-events! relay-config 1)
      (is (= 0 (:published (relay/relay-once! relay-config))))
      (is (empty? @sent)))))

(deftest lock-test
  (testing "a round skips while another instance holds the row of the relay"
    (let [{:keys [send!]} (recorder (constantly false))
          relay-config    (fresh-relay send!)
          held            (promise)
          release         (promise)
          relay-name      (:relay-name relay-config)
          holder          (future
                            (sql/transact!
                             (:sql-client relay-config)
                             (fn [tx]
                               (sql/execute-one! tx [hold-sql relay-name])
                               (deliver held true)
                               @release)))]
      @held
      (is (nil? (relay/relay-once! relay-config)))
      (deliver release true)
      @holder
      (is (some? (relay/relay-once! relay-config))))))
