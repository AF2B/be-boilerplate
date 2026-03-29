(ns com.borba.be-boilerplate.util.predicates
  "Common predicate functions used across specs and validations.")

(defn non-blank?
  "Returns true if s is a non-blank string."
  [s]
  (and (string? s) (seq (clojure.string/trim s))))

(defn positive-int?
  "Returns true if n is a positive integer."
  [n]
  (and (integer? n) (pos? n)))

(defn valid-email?
  "Basic email validation."
  [s]
  (and (string? s) (re-matches #"^[^\s@]+@[^\s@]+\.[^\s@]+$" s)))

(defn valid-uuid?
  "Returns true if s is a valid UUID string."
  [s]
  (and (string? s)
       (re-matches #"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$" s)))
