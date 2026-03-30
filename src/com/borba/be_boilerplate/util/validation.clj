(ns com.borba.be-boilerplate.util.validation
  "Spec-based validation helpers integrated with Railway."
  (:require [borba.railway :as rop]
            [clojure.spec.alpha :as s]))

(defn validate-spec
  "Validates data against a spec. Returns Right data or Left errors."
  [spec data]
  (if (s/valid? spec data)
    (rop/right data)
    (rop/left {:error   :validation-failed
               :spec    spec
               :explain (s/explain-str spec data)})))
