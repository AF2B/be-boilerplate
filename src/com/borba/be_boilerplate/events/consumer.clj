(ns com.borba.be-boilerplate.events.consumer
  "Kafka consumer handler functions.

   Pure business logic: no Integrant, no infrastructure imports. Handler
   functions are referenced in the system EDN by their fully-qualified symbol;
   the kafka-consumer component loads and resolves them.

   ── Adding a new handler ──────────────────────────────────────────────────

   1. Define the handler function here:
        (defn handle-payment-event [{:keys [value topic]}] ...)

   2. Reference it in system/stag.edn (and prod.edn):
        :kafka/consumer-handlers
        {\"payment-events\"
         com.borba.be-boilerplate.events.consumer/handle-payment-event}

   3. Add the topic to :components/kafka-consumer :topics.

   A handler receives {:keys [key value topic partition offset headers]},
   where :value is already read from JSON with keyword keys. Delivery is
   at-least-once, so a handler must be safe to run twice for a message."
  (:require
   [clojure.tools.logging :as log]))

(def ^:private unknown-event "unknown")

(defn handle-order-event
  "Handles a message of the order-events topic.
   - message: the message, a map of :key, :value, :topic and :offset"
  [message]
  (let [{message-key :key :keys [value topic offset]} message]
    (log/infof "received %s for order %s (%s@%d)"
               (:event-type value unknown-event)
               message-key
               topic
               offset)))
