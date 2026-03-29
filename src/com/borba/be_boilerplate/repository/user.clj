(ns com.borba.be-boilerplate.repository.user
  "Repository layer for User aggregate.
  
   All functions receive the components map and destructure
   the :sql-client for database access.
   
   Pattern:
     (defn find-by-id [components id]
       (let [{:keys [sql-client]} components]
         ...))"
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def ^:private create-table-sql
  "CREATE TABLE IF NOT EXISTS users (
     id         UUID PRIMARY KEY,
     name       VARCHAR(255) NOT NULL,
     email      VARCHAR(255) NOT NULL UNIQUE,
     created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
   );")

(defn ensure-table!
  "Creates the users table if it doesn't exist."
  [components]
  (let [{:keys [sql-client]} components]
    (jdbc/execute! sql-client [create-table-sql])))

(defn insert!
  "Inserts a new user. Returns the inserted row."
  [components {:keys [id name email]}]
  (let [{:keys [sql-client]} components]
    (jdbc/execute-one!
     sql-client
     ["INSERT INTO users (id, name, email) VALUES (?, ?, ?) RETURNING *"
      id name email]
     {:builder-fn rs/as-unqualified-kebab-maps})))

(defn find-by-id
  "Finds a user by UUID. Returns nil if not found."
  [components id]
  (let [{:keys [sql-client]} components]
    (jdbc/execute-one!
     sql-client
     ["SELECT * FROM users WHERE id = ?" id]
     {:builder-fn rs/as-unqualified-kebab-maps})))

(defn find-by-email
  "Finds a user by email. Returns nil if not found."
  [components email]
  (let [{:keys [sql-client]} components]
    (jdbc/execute-one!
     sql-client
     ["SELECT * FROM users WHERE email = ?" email]
     {:builder-fn rs/as-unqualified-kebab-maps})))

(defn find-all
  "Returns all users, ordered by created_at desc."
  [components]
  (let [{:keys [sql-client]} components]
    (jdbc/execute!
     sql-client
     ["SELECT * FROM users ORDER BY created_at DESC"]
     {:builder-fn rs/as-unqualified-kebab-maps})))
