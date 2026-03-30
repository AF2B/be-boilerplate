(ns com.borba.be-boilerplate.repository.user
  "Repository layer for User aggregate.

   Uses borba.sql-client API — no direct next.jdbc imports needed.
   All SQL result sets are auto-converted to unqualified kebab-case maps.

   Pattern:
     (defn find-by-id [components id]
       (let [{:keys [sql-client]} components]
         (sql/execute-one! sql-client [\"SELECT * FROM users WHERE id = ?\" id])))"
  (:require [borba.sql-client :as sql]))

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
    (sql/execute! sql-client [create-table-sql])))

(defn insert!
  "Inserts a new user. Returns the inserted row."
  [components {:keys [id name email]}]
  (let [{:keys [sql-client]} components]
    (sql/execute-one! sql-client
                      ["INSERT INTO users (id, name, email) VALUES (?, ?, ?) RETURNING *"
                       id name email])))

(defn find-by-id
  "Finds a user by UUID. Returns nil if not found."
  [components id]
  (let [{:keys [sql-client]} components]
    (sql/execute-one! sql-client
                      ["SELECT * FROM users WHERE id = ?" id])))

(defn find-by-email
  "Finds a user by email. Returns nil if not found."
  [components email]
  (let [{:keys [sql-client]} components]
    (sql/execute-one! sql-client
                      ["SELECT * FROM users WHERE email = ?" email])))

(defn find-all
  "Returns all users, ordered by created_at desc."
  [components]
  (let [{:keys [sql-client]} components]
    (sql/execute! sql-client
                  ["SELECT * FROM users ORDER BY created_at DESC"])))
