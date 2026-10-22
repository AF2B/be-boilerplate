(ns com.borba.be-boilerplate.specs.order
  "Specs for the Order domain.

   Contract
   --------
   Input (create): {:user-id <uuid string>
                    :items   [{:product  <string>
                               :quantity <int>
                               :price    <number>}]}
   Output:         {:id <UUID> :user-id <UUID> :items [...]
                    :total <BigDecimal> :status <keyword>}"
  (:require [clojure.spec.alpha :as s]
            [com.borba.be-boilerplate.util.predicates :as pred]))

;; ── Primitive Specs ─────────────────────────────────────────────────────────

(s/def ::id uuid?)

(s/def ::user-id pred/valid-uuid?)

(s/def :com.borba.be-boilerplate.specs.order.stored/user-id uuid?)

(s/def ::product (s/and string? pred/non-blank?))

(s/def ::quantity pred/positive-int?)

(s/def ::price (s/and number? pos?))

(s/def ::status #{:pending :confirmed :shipped :delivered :cancelled})

(s/def ::total (s/and number? #(>= % 0)))

;; ── Item Specs ──────────────────────────────────────────────────────────────

(s/def ::item
  (s/keys :req-un [::product ::quantity ::price]))

(s/def ::items
  (s/and (s/coll-of ::item :min-count 1) vector?))

;; ── Composite Specs ─────────────────────────────────────────────────────────

(s/def ::create-input
  (s/keys :req-un [::user-id ::items]))

(s/def ::order
  (s/keys :req-un [::id
                   :com.borba.be-boilerplate.specs.order.stored/user-id
                   ::items
                   ::total
                   ::status]))
