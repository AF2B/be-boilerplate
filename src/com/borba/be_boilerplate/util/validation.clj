(ns com.borba.be-boilerplate.util.validation
  "Spec-based validation helpers integrated with Railway.")

(defn validate-spec
  "Validates data against a spec. Returns Right data or Left errors."
  [spec data]
  (require '[com.borba.be-boilerplate.util.railway :as rop])
  (require '[clojure.spec.alpha :as s])
  (if (s/valid? spec data)
    ((resolve 'com.borba.be-boilerplate.util.railway/right) data)
    ((resolve 'com.borba.be-boilerplate.util.railway/left)
     {:error   :validation-failed
      :spec    spec
      :explain (s/explain-str spec data)})))
