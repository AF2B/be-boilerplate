(ns com.borba.be-boilerplate.components.kafka-consumer
  "Kafka consumer component for be-boilerplate.

   The ig/init-key :components/kafka-consumer and :kafka/consumer-handlers
   are registered by borba-kafka-consumer-component.

   Override the handler map in events/consumer.clj:

     (defmethod ig/init-key :kafka/consumer-handlers [_ _]
       {\"order-events\"   handle-order-event
        \"payment-events\" handle-payment-event})

   Handler functions receive:
     {:keys [key value topic partition offset]}
   where :value is already parsed from JSON.

   See borba-kafka-consumer-component/src/borba/kafka_consumer.clj for details."
  (:require [borba.kafka-consumer]))
