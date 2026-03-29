(ns com.borba.be-boilerplate.handlers.business.user
  "Business logic for User domain.
  
   Uses Railway Oriented Programming — every function returns Right or Left.
   Components are injected via the :components key in the interceptor context."
  (:require [clojure.spec.alpha :as s]
            [com.borba.be-boilerplate.specs.user :as specs]
            [com.borba.be-boilerplate.repository.user :as repo]
            [com.borba.be-boilerplate.util.railway :as rop])
  (:import (java.util UUID)
           (java.time Instant)))

;; ── Railway steps ───────────────────────────────────────────────────────────

(defn- validate-create-input
  "Validates user creation input against spec."
  [input]
  (if (s/valid? ::specs/create-input input)
    (rop/right input)
    (rop/left {:error   :validation-failed
               :details (s/explain-str ::specs/create-input input)})))

(defn- check-email-unique
  "Ensures the email is not already taken."
  [components]
  (fn [input]
    (if (repo/find-by-email components (:email input))
      (rop/left {:error :email-already-exists
                 :email (:email input)})
      (rop/right input))))

(defn- enrich-user
  "Adds generated ID and timestamp to user data."
  [input]
  (rop/right
   (assoc input
          :id         (str (UUID/randomUUID))
          :created-at (Instant/now))))

(defn- persist-user
  "Persists user to database."
  [components]
  (fn [user]
    (rop/try-right repo/insert! components user)))

;; ── Public API ──────────────────────────────────────────────────────────────

(defn create!
  "Creates a new user using Railway Oriented Programming.
   
   Pipeline:
     validate → check-unique → enrich → persist
   
   Returns Right{:id :name :email :created-at} or Left{:error ...}"
  [components input]
  (rop/|> input
          validate-create-input
          (check-email-unique components)
          enrich-user
          (persist-user components)))

(defn get-by-id
  "Retrieves a user by ID. Returns Right{user} or Left{:error :not-found}."
  [components id]
  (if-let [user (repo/find-by-id components id)]
    (rop/right user)
    (rop/left {:error :not-found
               :id    id})))
