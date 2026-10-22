(ns com.borba.be-boilerplate.system.projection
  "Projections of the event store: the functions that fold the events of an
   aggregate into a read model, keyed by event type. Empty in the boilerplate;
   a service adds its folds here and reads them with
   borba.event-store/get-aggregate-snapshot."
  (:require
   [integrant.core :as ig]))

(defmethod ig/init-key :projection/folding-funcs
  [_key folding-funcs]
  folding-funcs)
