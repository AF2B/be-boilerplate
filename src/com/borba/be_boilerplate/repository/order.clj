(ns com.borba.be-boilerplate.repository.order
  "Repository layer for Order aggregate.
  
   Follows the same components destructuring pattern:
     (let [{:keys [sql-client]} components] ...)"
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [cheshire.core :as json]))

(def ^:private create-table-sql
  "CREATE TABLE IF NOT EXISTS orders (
     id         UUID PRIMARY KEY,
     user_id    UUID NOT NULL,
     items      JSONB NOT NULL,
     total      DECIMAL(12,2) NOT NULL,
     status     VARCHAR(50) NOT NULL DEFAULT 'pending',
     created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
   );
   CREATE INDEX IF NOT EXISTS idx_orders_user ON orders (user_id);")

(defn ensure-table!
  "Creates the orders table if it doesn't exist."
  [components]
  (let [{:keys [sql-client]} components]
    (jdbc/execute! sql-client [create-table-sql])))

(defn insert!
  "Inserts a new order. Returns the inserted row."
  [components {:keys [id user-id items total status]}]
  (let [{:keys [sql-client]} components]
    (jdbc/execute-one!
     sql-client
     ["INSERT INTO orders (id, user_id, items, total, status)
       VALUES (?, ?, ?::jsonb, ?, ?)
       RETURNING *"
      id user-id (json/generate-string items) total (or status "pending")]
     {:builder-fn rs/as-unqualified-kebab-maps})))

(defn find-by-id
  "Finds an order by UUID."
  [components id]
  (let [{:keys [sql-client]} components]
    (jdbc/execute-one!
     sql-client
     ["SELECT * FROM orders WHERE id = ?" id]
     {:builder-fn rs/as-unqualified-kebab-maps})))

(defn find-by-user
  "Finds all orders for a given user."
  [components user-id]
  (let [{:keys [sql-client]} components]
    (jdbc/execute!
     sql-client
     ["SELECT * FROM orders WHERE user_id = ? ORDER BY created_at DESC" user-id]
     {:builder-fn rs/as-unqualified-kebab-maps})))
