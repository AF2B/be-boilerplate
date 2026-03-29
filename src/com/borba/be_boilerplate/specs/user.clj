(ns com.borba.be-boilerplate.specs.user
  "Specs for the User domain.

   Contract
   --------
   Input (create):  {:name <string> :email <string>}
   Output:          {:id <uuid> :name <string> :email <string> :created-at <inst>}"
  (:require [clojure.spec.alpha :as s]
            [com.borba.be-boilerplate.util.predicates :as pred]))

;; ── Primitive Specs ─────────────────────────────────────────────────────────

(s/def ::id pred/valid-uuid?)

(s/def ::name (s/and string? pred/non-blank? #(<= (count %) 255)))

(s/def ::email (s/and string? pred/valid-email?))

(s/def ::created-at inst?)

;; ── Composite Specs ─────────────────────────────────────────────────────────

(s/def ::create-input
  (s/keys :req-un [::name ::email]))

(s/def ::user
  (s/keys :req-un [::id ::name ::email ::created-at]))

(s/def ::user-response
  (s/keys :req-un [::id ::name ::email]))
