(ns ^:integration flows.order-flow-test
  "Integration flows for the Order endpoints, through the real chain, a real
   PostgreSQL and the event store."
  (:require
   [borba.event-store :as es]
   [borba.flow :as sf]
   [clojure.test :refer [is use-fixtures]]
   [com.borba.be-boilerplate.handlers.http.handlers]
   [flows.support :as support]
   [state-flow.api :as flow]))

(use-fixtures :once support/with-components)

(def ^:private user-id "550e8400-e29b-41d4-a716-446655440000")

(sf/defflow order-endpoints
  {:routes     [["/v1/orders"     :post :order/create]
                ["/v1/orders/:id" :get  :order/get-by-id]]
   :components (support/components)}

  ;; ── Create order ──────────────────────────────────────────────────────────
  [created (sf/request :post "/v1/orders"
                       {:body {:user-id user-id
                               :items   [{:product  "Clojure in Action"
                                          :quantity 1
                                          :price    49.90}
                                         {:product  "REPL Sticker"
                                          :quantity 3
                                          :price    5.00}]}})]
  (flow/return
   (let [body (sf/json-body created)]
     (is (= 201 (:status created)) "create order: status 201")
     (is (some? (:id body)) "create order: has id")
     (is (= 64.9 (:total body)) "create order: correct total")
     (is (= "pending" (:status body)) "create order: pending")))

  ;; ── The event was stored with the order ───────────────────────────────────
  [order-id (flow/return (:id (sf/json-body created)))]
  (flow/return
   (let [events (es/get-events (:event-store (support/components)) order-id)]
     (is (= ["order-created"] (map :event-type events))
         "create order: one order-created event")
     (is (= 1 (:version (first events))) "create order: version 1")))

  ;; ── Read it back ──────────────────────────────────────────────────────────
  [found (sf/request :get (str "/v1/orders/" order-id))]
  (flow/return
   (let [body (sf/json-body found)]
     (is (= 200 (:status found)) "get order: status 200")
     (is (= 2 (count (:items body))) "get order: items read back")))

  ;; ── Create order with missing items ───────────────────────────────────────
  [resp (sf/request :post "/v1/orders" {:body {:user-id user-id}})]
  (flow/return
   (is (= 422 (:status resp)) "order missing items: status 422"))

  ;; ── Create order with invalid user-id ─────────────────────────────────────
  [resp (sf/request :post "/v1/orders"
                    {:body {:user-id "not-a-uuid"
                            :items   [{:product "X" :quantity 1 :price 10}]}})]
  (flow/return
   (is (= 422 (:status resp)) "order invalid user-id: status 422")))
