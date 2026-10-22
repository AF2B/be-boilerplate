(ns com.borba.be-boilerplate.handlers.business.order
  "Business logic for the Order domain.

   Demonstrates Event Sourcing + Railway Oriented Programming. Creating an
   order stores the order and its order-created event in one transaction, so
   neither exists without the other, and then publishes the event to Kafka."
  (:require
   [borba.event-store :as es]
   [borba.railway :as rop]
   [borba.sql-client :as sql]
   [clojure.spec.alpha :as s]
   [clojure.tools.logging :as log]
   [com.borba.be-boilerplate.events.producer :as producer]
   [com.borba.be-boilerplate.repository.order :as repo]
   [com.borba.be-boilerplate.specs.order :as specs]))

(def ^:private aggregate-type "order")

(def ^:private created-event "order-created")

;; An order-created event opens the history of the aggregate.
(def ^:private first-version 1)

(def ^:private initial-status :pending)

;; ── Railway steps ───────────────────────────────────────────────────────────

(defn- validate-create-input
  "Validates order creation input."
  [input]
  (if (s/valid? ::specs/create-input input)
    (rop/right input)
    (rop/failure :validation-failed
                 {:details (s/explain-str ::specs/create-input input)})))

(defn- line-total
  [{:keys [quantity price]}]
  (* quantity (bigdec price)))

(defn- calculate-total
  "Adds the total of the items, as a BigDecimal, so money keeps its cents."
  [input]
  (let [total (reduce + 0M (map line-total (:items input)))]
    (rop/right (assoc input :total total))))

(defn- enrich-order
  "Adds the generated id, the user id as a UUID and the initial status."
  [input]
  (rop/right
   (assoc input
          :id      (random-uuid)
          :user-id (parse-uuid (:user-id input))
          :status  initial-status)))

(defn- created-event-of
  [order]
  {:aggregate-id   (:id order)
   :aggregate-type aggregate-type
   :event-type     created-event
   :payload        order
   :version        first-version})

(defn- persist-with-event!
  "Stores the order and its order-created event in one transaction. A version
   conflict from the event store throws its data, which rolls the order back
   and becomes the failure."
  [{:keys [sql-client event-store] :as components} order]
  (sql/transact!
   sql-client
   (fn [tx]
     ;; The store is a map of its :datasource; the connection of the
     ;; transaction takes its place, as it does for the repository.
     (let [stored   (repo/insert! (assoc components :sql-client tx) order)
           appended (es/append! (assoc event-store :datasource tx)
                                (created-event-of stored))]
       (when (:error appended)
         (throw (ex-info "the order-created event was not stored" appended)))
       stored))))

(defn- persist-order
  "Persists the order and its event."
  [components]
  (fn [order]
    (rop/try-right persist-with-event! components order)))

(defn- publish-created!
  "Publishes the event without failing the request: the order and its event
   are already stored, and a projection of the event store can publish again."
  [kafka-producer order]
  (try
    (producer/order-created! kafka-producer order)
    (catch Exception cause
      (log/warn cause "order-created was not published for" (:id order)))))

(defn- publish-event
  "Publishes the order-created event to Kafka."
  [{:keys [kafka-producer]}]
  (fn [order]
    (when kafka-producer
      (publish-created! kafka-producer order))
    (rop/right order)))

;; ── Public API ──────────────────────────────────────────────────────────────

(defn create!
  "Creates an order: validate, calculate the total, enrich, persist with its
   event, publish. Returns Right{order} or Left{:error ...}.
   - components: the service components, with :sql-client, :event-store and,
     optionally, :kafka-producer
   - input: the request body, {:user-id <uuid string> :items [...]}"
  [components input]
  (rop/|> input
          validate-create-input
          calculate-total
          enrich-order
          (persist-order components)
          (publish-event components)))

(defn get-by-id
  "Returns Right{order}, or Left{:error :not-found} when there is no order with
   the id or the id is not a UUID.
   - components: the service components, with :sql-client
   - id: the id of the order, as text"
  [components id]
  (if-let [order (some->> (parse-uuid id) (repo/find-by-id components))]
    (rop/right order)
    (rop/failure :not-found {:id id})))
