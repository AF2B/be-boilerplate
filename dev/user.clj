(ns user
  "REPL helpers: start and stop the service as the command line does, without
   the shutdown hook. Start a REPL with `clojure -M:dev` and the
   variables of .env in the environment."
  (:require
   [borba.core.main :as main]
   [borba.core.system :as system]))

(def ^:private default-profile :stag)

(defn go
  "Starts the service and returns its system.
   - profile: the profile to read the configuration for (default :stag)"
  ([]
   (go default-profile))
  ([profile]
   (main/run {:profile profile :hook? false})))

(defn halt
  "Stops the running service, if there is one."
  []
  (system/stop!))

(defn running
  "Returns the running system, or nil."
  []
  (system/current))
