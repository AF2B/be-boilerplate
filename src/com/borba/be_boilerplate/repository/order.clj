(ns com.borba.be-boilerplate.repository.order
  "Repository layer for the Order aggregate.

   Uses the borba.sql-client API, no direct next.jdbc. The items are a JSONB
   column: they are written as JSON text cast to jsonb, and read back with
   keyword keys and BigDecimal numbers, so a price keeps its cents."
  (:require
   [borba.sql-client :as sql]
   [jsonista.core :as json])
  (:import
   (org.postgresql.util PGobject)))

(set! *warn-on-reflection* true)

(def ^:private table :orders)

(def ^:private insert-sql
  (str "INSERT INTO orders (id, user_id, items, total, status)"
       " VALUES (?, ?, ?::jsonb, ?, ?) RETURNING *"))

(def ^:private find-by-user-sql
  "SELECT * FROM orders WHERE user_id = ? ORDER BY created_at DESC")

(def ^:private items-mapper
  (json/object-mapper {:decode-key-fn true
                       :bigdecimals   true}))

(defn- decode-items
  [^PGobject items]
  (json/read-value (.getValue items) items-mapper))

(defn- row->order
  [row]
  (some-> row
          (update :items decode-items)
          (update :status keyword)))

(defn insert!
  "Inserts an order and returns it as stored.
   - components: the service components, with :sql-client (a data source or
     the connection of a transaction)
   - order: a map of :id and :user-id (UUIDs), :items, :total (BigDecimal) and
     :status (keyword)"
  [components order]
  (let [{:keys [id user-id items total status]} order]
    (row->order
     (sql/execute-one! (:sql-client components)
                       [insert-sql
                        id
                        user-id
                        (json/write-value-as-string items)
                        total
                        (name status)]))))

(defn find-by-id
  "Returns the order with an id, or nil.
   - components: the service components, with :sql-client
   - id: the UUID of the order"
  [components id]
  (row->order (sql/find-one-by! (:sql-client components) table {:id id})))

(defn find-by-user
  "Returns the orders of a user, the most recent first.
   - components: the service components, with :sql-client
   - user-id: the UUID of the user"
  [components user-id]
  (mapv row->order
        (sql/execute! (:sql-client components) [find-by-user-sql user-id])))
