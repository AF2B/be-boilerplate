(ns generators.order
  "Test data generators for Order domain."
  (:require [clojure.test.check.generators :as gen]))

(def gen-product-name
  (gen/elements ["Widget" "Gadget" "Clojure Book" "REPL Sticker"
                 "Lambda Pin" "Functional Mug" "Monad T-Shirt"]))

(def gen-item
  "Generator for a single order item."
  (gen/hash-map
   :product  gen-product-name
   :quantity (gen/choose 1 10)
   :price    (gen/fmap #(/ (double %) 100.0) (gen/choose 100 99999))))

(def gen-items
  "Generator for a non-empty vector of items."
  (gen/not-empty (gen/vector gen-item 1 5)))

(def gen-order-input
  "Generator for valid order creation input."
  (gen/hash-map
   :user-id (gen/fmap str (gen/return (java.util.UUID/randomUUID)))
   :items   gen-items))
