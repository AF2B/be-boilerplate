(ns com.borba.be-boilerplate.repository.user
  "Repository layer for the User aggregate.

   Uses the borba.sql-client API, no direct next.jdbc. Rows come back as maps
   with unqualified kebab-case keys (`created_at` is `:created-at`), and every
   timestamp column is read as a java.time.Instant.

   Pattern:
     (defn find-by-id [components id]
       (sql/find-one-by! (:sql-client components) :users {:id id}))"
  (:require
   [borba.sql-client :as sql]))

(def ^:private table :users)

(def ^:private columns [:id :name :email :created-at])

(defn insert!
  "Inserts a user and returns the stored row. A repeated email throws the
   SQLException of the unique constraint, which sql/error-data classifies.
   - components: the service components, with :sql-client
   - user: a map of :id (UUID), :name, :email and :created-at (Instant)"
  [components user]
  (sql/insert! (:sql-client components) table (select-keys user columns)))

(defn find-by-id
  "Returns the user with an id, or nil.
   - components: the service components, with :sql-client
   - id: the UUID of the user"
  [components id]
  (sql/find-one-by! (:sql-client components) table {:id id}))

(defn find-by-email
  "Returns the user with an email, or nil.
   - components: the service components, with :sql-client
   - email: the email of the user"
  [components email]
  (sql/find-one-by! (:sql-client components) table {:email email}))

(defn find-all
  "Returns every user, the most recent first.
   - components: the service components, with :sql-client"
  [components]
  (sql/execute! (:sql-client components)
                ["SELECT * FROM users ORDER BY created_at DESC"]))
