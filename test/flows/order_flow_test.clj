(ns flows.order-flow-test
  "Integration flow tests for Order endpoints using state-flow."
  (:require [borba.flow :as sf]
            [borba.routes.component]
            [clojure.test :refer [is]]
            [com.borba.be-boilerplate.handlers.http.handlers]
            [state-flow.api :as flow]))

(sf/defflow run-order-integration-tests
  [["/v1/orders"    :post :order/create]
   ["/v1/orders/:id" :get :order/get-by-id]]

  ;; ── Create order ──────────────────────────────────────────────────────────
  [resp (sf/request :post "/v1/orders"
                    {:body {:user-id "550e8400-e29b-41d4-a716-446655440000"
                            :items   [{:product  "Clojure in Action"
                                       :quantity 1
                                       :price    49.90}
                                      {:product  "REPL Sticker"
                                       :quantity 3
                                       :price    5.00}]}})]
  (flow/return
   (let [body (sf/json-body resp)]
     (do (is (= 201 (:status resp))      "create order: status 201")
         (is (some? (:id body))           "create order: has id")
         (is (= 64.9 (:total body))      "create order: correct total"))))

  ;; ── Create order with missing items ───────────────────────────────────────
  [resp (sf/request :post "/v1/orders"
                    {:body {:user-id "550e8400-e29b-41d4-a716-446655440000"}})]
  (flow/return
   (is (= 422 (:status resp)) "order missing items: status 422"))

  ;; ── Create order with invalid user-id ─────────────────────────────────────
  [resp (sf/request :post "/v1/orders"
                    {:body {:user-id "not-a-uuid"
                            :items   [{:product "X" :quantity 1 :price 10}]}})]
  (flow/return
   (is (= 422 (:status resp)) "order invalid user-id: status 422")))
