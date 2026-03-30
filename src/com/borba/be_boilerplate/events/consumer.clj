(ns com.borba.be-boilerplate.events.consumer
  "Kafka consumer handler functions.

   Pure business logic — no Integrant, no infrastructure imports.
   Handler functions are referenced directly in the system EDN via
   their fully-qualified symbol; the kafka-consumer component loads
   and resolves them automatically.

   ── Adding a new handler ──────────────────────────────────────────────────────

   1. Define the handler function here:
        (defn handle-payment-event [{:keys [value topic]}] ...)

   2. Reference it in system/stag.edn (and prod.edn):
        :kafka/consumer-handlers
        {\"payment-events\" com.borba.be-boilerplate.events.consumer/handle-payment-event}

   3. Add the topic to :components/kafka-consumer :topics in stag.edn.

   Handler functions receive:
     {:keys [key value topic partition offset]}
   where :value is already parsed from JSON.")

(defn handle-order-event
  "Processes order events from the 'order-events' topic."
  [{:keys [key value topic offset]}]
  (println "📥 Received order event:"
           {:key    key
            :topic  topic
            :offset offset
            :event  (:event-type value "unknown")}))
