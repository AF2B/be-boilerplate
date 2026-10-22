(ns com.borba.be-boilerplate.events.producer
  "Domain events published to Kafka.

   Thin, domain-named wrappers around the kafka-producer component, which is
   injected through the components:

     (let [{:keys [kafka-producer]} components]
       (producer/order-created! kafka-producer order))

   A message is an envelope of :event-type and :data, sent as JSON with the id
   of the aggregate as its key, so the events of an aggregate stay in order."
  (:require
   [borba.kafka-producer :as kafka]))

(def order-events-topic
  "The topic of the order events."
  "order-events")

(def ^:private order-created "order-created")

(defn order-created!
  "Publishes an order-created event, and returns the future of where it was
   stored. A failure to deliver it is logged by the producer.
   - kafka-producer: the kafka-producer component
   - order: the order as it was stored"
  [kafka-producer order]
  (kafka/send! kafka-producer
               order-events-topic
               (:id order)
               {:event-type order-created
                :data       order}))
