(ns com.borba.be-boilerplate.util.predicates
  "Common predicate functions used across specs and validations."
  (:require
   [clojure.string :as str]))

(def ^:private email-pattern #"^[^\s@]+@[^\s@]+\.[^\s@]+$")

(defn non-blank?
  "Returns true if s is a string with something other than whitespace.
   - s: any value"
  [s]
  (and (string? s) (not (str/blank? s))))

(defn positive-int?
  "Returns true if n is a positive integer.
   - n: any value"
  [n]
  (and (integer? n) (pos? n)))

(defn valid-email?
  "Returns true if s looks like an email address (basic validation).
   - s: any value"
  [s]
  (and (string? s) (some? (re-matches email-pattern s))))

(defn valid-uuid?
  "Returns true if s is the text of a UUID.
   - s: any value"
  [s]
  (and (string? s) (some? (parse-uuid s))))
