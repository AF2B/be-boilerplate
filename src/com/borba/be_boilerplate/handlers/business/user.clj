(ns com.borba.be-boilerplate.handlers.business.user
  "Business logic for the User domain.

   Uses Railway Oriented Programming: every step returns Right or Left, and a
   failure is data with an :error code that the HTTP layer maps to a status."
  (:require
   [borba.railway :as rop]
   [borba.sql-client :as sql]
   [clojure.spec.alpha :as s]
   [com.borba.be-boilerplate.repository.user :as repo]
   [com.borba.be-boilerplate.specs.user :as specs])
  (:import
   (java.sql SQLException)
   (java.time Instant)))

(set! *warn-on-reflection* true)

;; ── Railway steps ───────────────────────────────────────────────────────────

(defn- validate-create-input
  "Validates user creation input against spec."
  [input]
  (if (s/valid? ::specs/create-input input)
    (rop/right input)
    (rop/failure :validation-failed
                 {:details (s/explain-str ::specs/create-input input)})))

(defn- email-taken
  [email]
  (rop/failure :user/email-already-exists {:email email}))

(defn- check-email-unique
  "Ensures the email is not already taken."
  [components]
  (fn [input]
    (if (repo/find-by-email components (:email input))
      (email-taken (:email input))
      (rop/right input))))

(defn- enrich-user
  "Adds the generated id and the creation time."
  [input]
  (rop/right
   (assoc input
          :id         (random-uuid)
          :created-at (Instant/now))))

(defn- persist-user
  "Persists the user. Two requests with the same email can both pass the check,
   so the unique constraint has the last word, and is the same failure."
  [components]
  (fn [user]
    (try
      (rop/right (repo/insert! components user))
      (catch SQLException cause
        (if (= :unique-violation (:error (sql/error-data cause)))
          (email-taken (:email user))
          (throw cause))))))

;; ── Public API ──────────────────────────────────────────────────────────────

(defn create!
  "Creates a user: validate, check the email, enrich, persist.
   Returns Right{user} or Left{:error ...}.
   - components: the service components, with :sql-client
   - input: the request body, {:name <string> :email <string>}"
  [components input]
  (rop/|> input
          validate-create-input
          (check-email-unique components)
          enrich-user
          (persist-user components)))

(defn get-by-id
  "Returns Right{user}, or Left{:error :not-found} when there is no user with
   the id or the id is not a UUID.
   - components: the service components, with :sql-client
   - id: the id of the user, as text"
  [components id]
  (if-let [user (some->> (parse-uuid id) (repo/find-by-id components))]
    (rop/right user)
    (rop/failure :not-found {:id id})))
