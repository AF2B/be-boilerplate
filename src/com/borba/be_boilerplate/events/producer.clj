(ns com.borba.be-boilerplate.events.producer
  "The messages that the events of the event store become on Kafka.

   The request never publishes: it stores the state and the event in one
   transaction, and the relay (system/event_relay.clj) publishes the events of
   the event store, in the order they were stored in, each as an envelope of
   :event-type and :data with the id of its aggregate as its key, so the events
   of an aggregate stay in order.

   The envelope is the contract of its topic: events/schemas/order_events.edn
   for order-events, which the Schema Registry holds and every value is checked
   against before it is sent.")

(defn event-message
  "Returns the message an event of the event store is published as.
   - event: the event, as the event store returns it"
  [event]
  {:event-type (:event-type event)
   :data       (:payload event)})
