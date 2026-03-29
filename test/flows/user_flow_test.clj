(ns flows.user-flow-test
  "Integration flow tests for User endpoints using state-flow."
  (:require [borba.flow :as sf]
            [borba.routes.component]
            [clojure.test :refer [is]]
            [com.borba.be-boilerplate.handlers.http.routes]
            [state-flow.api :as flow]))

(sf/defflow run-user-integration-tests
  [["/health"       :get  :health/check]
   ["/v1/users"     :post :user/create]
   ["/v1/users/:id" :get  :user/get-by-id]]

  ;; ── Health check ──────────────────────────────────────────────────────────
  [resp (sf/request :get "/health")]
  (flow/return
   (do (is (= 200 (:status resp))     "health: status 200")
       (is (= "UP" (:status (sf/json-body resp))) "health: status UP")))

  ;; ── Create user ───────────────────────────────────────────────────────────
  [resp (sf/request :post "/v1/users"
                    {:body {:name  "André Borba"
                            :email "andre@borba.com"}})]
  (flow/return
   (do (is (= 201 (:status resp))        "create user: status 201")
       (is (some? (:id (sf/json-body resp))) "create user: has id")))

  ;; ── Create user with invalid input ────────────────────────────────────────
  [resp (sf/request :post "/v1/users"
                    {:body {:name ""}})]
  (flow/return
   (is (= 422 (:status resp)) "create user invalid: status 422"))

  ;; ── Duplicate email ───────────────────────────────────────────────────────
  [resp (sf/request :post "/v1/users"
                    {:body {:name  "Duplicate"
                            :email "andre@borba.com"}})]
  (flow/return
   (is (= 409 (:status resp)) "duplicate email: status 409")))
