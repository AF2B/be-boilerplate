(ns generators.user
  "Test data generators for User domain."
  (:require [clojure.test.check.generators :as gen]))

(def gen-name
  "Generator for valid user names."
  (gen/fmap
   (fn [[first last]]
     (str first " " last))
   (gen/tuple
    (gen/elements ["André" "Maria" "João" "Ana" "Pedro" "Clara" "Lucas" "Julia"])
    (gen/elements ["Borba" "Silva" "Santos" "Oliveira" "Souza" "Lima" "Costa"]))))

(def gen-email
  "Generator for valid email addresses."
  (gen/fmap
   (fn [[user domain]]
     (str user "@" domain ".com"))
   (gen/tuple
    (gen/fmap #(clojure.string/lower-case %) gen-name)
    (gen/elements ["gmail" "outlook" "borba" "company" "test"]))))

(def gen-user-input
  "Generator for valid user creation input."
  (gen/hash-map
   :name  gen-name
   :email gen-email))

(def gen-uuid
  "Generator for UUID strings."
  (gen/fmap str (gen/return (java.util.UUID/randomUUID))))
