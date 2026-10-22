(ns com.borba.be-boilerplate.system.components
  "The map of components every handler and interceptor receives.

   No library registers :service/components: it is the service that decides
   what its handlers can reach. The value is the map itself, with the started
   components in the place of the references:

     :service/components
     {:sql-client  #ig/ref :components/database
      :event-store #ig/ref :components/event-store}"
  (:require
   [integrant.core :as ig]))

(defmethod ig/init-key :service/components
  [_ components]
  components)
