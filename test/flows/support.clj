(ns flows.support
  "Starts, for the integration flows, the components of the test profile that
   the handlers need: the database and the event store, against the services
   of docker-compose.yml or of the pipeline."
  (:require
   [borba.core.config :as config]
   [borba.event-store]
   [borba.sql-client]
   [integrant.core :as ig]))

(def ^:private profile :test)

(def ^:private started-keys [:components/database :components/event-store])

(defonce ^:private system (atom nil))

(defn components
  "Returns the components the handlers are built with in a flow. Kafka is
   left out, so the flows do not need a broker."
  []
  (let [started @system]
    {:sql-client  (:components/database started)
     :event-store (:components/event-store started)}))

(defn with-components
  "A :once fixture that starts the components for the tests of a namespace
   and halts them after.
   - run-tests: the function that runs the tests"
  [run-tests]
  (let [system-config (:ig/system (config/load-config profile))]
    (reset! system (ig/init system-config started-keys))
    (try
      (run-tests)
      (finally
        (ig/halt! @system)
        (reset! system nil)))))
