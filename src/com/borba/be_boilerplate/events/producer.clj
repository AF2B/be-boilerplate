(ns com.borba.be-boilerplate.events.producer
  "Convenience namespace for publishing domain events to Kafka.
  
   This namespace provides domain-specific wrappers around the
   generic kafka-producer component. The actual producer is injected
   via components.
   
   Usage in business layer:
     (let [{:keys [kafka-producer]} components]
       (producer/order-created! kafka-producer order))"
  (:require [borba.kafka-producer :as kafka]))

(defn order-created!
  "Publishes an order-created event."
  [kafka-producer order]
  (kafka/send! kafka-producer
               "order-events"
               (:id order)
               {:event-type "order-created"
                :data       order}))

(defn order-confirmed!
  "Publishes an order-confirmed event."
  [kafka-producer order-id]
  (kafka/send! kafka-producer
               "order-events"
               order-id
               {:event-type "order-confirmed"
                :data       {:order-id order-id}}))

(defn user-created!
  "Publishes a user-created event."
  [kafka-producer user]
  (kafka/send! kafka-producer
               "user-events"
               (:id user)
               {:event-type "user-created"
                :data       user}))
