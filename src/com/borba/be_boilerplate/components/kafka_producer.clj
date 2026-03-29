(ns com.borba.be-boilerplate.components.kafka-producer
  "Kafka producer component for be-boilerplate.

   The ig/init-key :components/kafka-producer is registered by
   borba-kafka-producer-component.

   For publishing messages, prefer borba.kafka-producer functions:

     (require '[borba.kafka-producer :as kafka])

     (kafka/send!       producer \"order-events\" order-id payload)
     (kafka/send-sync!  producer \"payments\"     payment-id payload)
     (kafka/send-batch! producer \"order-events\" [{:key k1 :value v1}])

   See borba-kafka-producer-component/src/borba/kafka_producer.clj for the full API."
  (:require [borba.kafka-producer]))
