(ns generators.user
  "Test data generators for the User domain."
  (:require
   [clojure.string :as str]
   [clojure.test.check.generators :as gen]))

(def gen-name
  "Generator for valid user names."
  (gen/fmap
   (fn [[given-name family-name]]
     (str given-name " " family-name))
   (gen/tuple
    (gen/elements ["André" "Maria" "João" "Ana" "Pedro" "Clara" "Lucas"])
    (gen/elements ["Borba" "Silva" "Santos" "Oliveira" "Souza" "Lima"]))))

(def gen-email
  "Generator for valid email addresses."
  (gen/fmap
   (fn [[full-name domain]]
     (str (str/replace (str/lower-case full-name) " " ".") "@" domain ".com"))
   (gen/tuple
    gen-name
    (gen/elements ["gmail" "outlook" "borba" "company" "test"]))))

(def gen-user-input
  "Generator for valid user creation input."
  (gen/hash-map
   :name  gen-name
   :email gen-email))
