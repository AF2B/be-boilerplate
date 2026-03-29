(ns com.borba.be-boilerplate.events.consumer
  "Kafka consumer handler functions.
  
   Each function processes a message from a specific topic.
   Handlers are registered via defmethod ig/init-key :kafka/consumer-handlers
   and referenced in the system EDN.
   
   Pattern:
     (defmethod ig/init-key :kafka/consumer-handlers [_ _]
       {\"order-events\" handle-order-event})"
  (:require [integrant.core :as ig]))

(defn handle-order-event
  "Processes order events from the 'order-events' topic.
   
   This is a demo handler — extend it for your domain logic.
   Receives a map: {:key <string> :value <parsed-json> :topic :partition :offset}"
  [{:keys [key value topic offset]}]
  (println "📥 Received order event:"
           {:key    key
            :topic  topic
            :offset offset
            :event  (:event-type value "unknown")}))

;; ── Register consumer handlers ──────────────────────────────────────────────
;; Override the base empty map from kafka_consumer.clj

(defmethod ig/init-key :kafka/consumer-handlers
  [_ _]
  {"order-events" handle-order-event})
