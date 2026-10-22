(ns generators.order
  "Test data generators for the Order domain."
  (:require
   [clojure.test.check.generators :as gen]))

(def gen-product-name
  "Generator for product names."
  (gen/elements ["Widget" "Gadget" "Clojure Book" "REPL Sticker"
                 "Lambda Pin" "Functional Mug" "Monad T-Shirt"]))

(def gen-price
  "Generator for prices from 1.00 to 999.99, with cents."
  (gen/fmap #(/ (bigdec %) 100M) (gen/choose 100 99999)))

(def gen-item
  "Generator for a single order item."
  (gen/hash-map
   :product  gen-product-name
   :quantity (gen/choose 1 10)
   :price    gen-price))

(def gen-items
  "Generator for a non-empty vector of items."
  (gen/vector gen-item 1 5))

(def gen-order-input
  "Generator for valid order creation input."
  (gen/hash-map
   :user-id (gen/fmap str gen/uuid)
   :items   gen-items))
