(ns com.borba.be-boilerplate.components.event-store
  "Event store component for be-boilerplate.

   The ig/init-key :components/event-store is registered by
   borba-event-store-component.

   For Event Sourcing operations, prefer borba.event-store functions:

     (require '[borba.event-store :as es])

     (es/append! event-store
       {:aggregate-id   order-id
        :aggregate-type \"order\"
        :event-type     \"order.created\"
        :payload        order-data
        :version        (inc (es/get-latest-version event-store order-id))})

     (es/get-events         event-store aggregate-id)
     (es/get-events-by-type event-store \"order.created\")
     (es/get-latest-version event-store aggregate-id)
     (es/get-aggregate-snapshot event-store aggregate-id reducer-fn)

   See borba-event-store-component/src/borba/event_store.clj for the full API."
  (:require [borba.event-store]))
