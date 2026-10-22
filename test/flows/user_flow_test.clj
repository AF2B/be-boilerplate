(ns ^:integration flows.user-flow-test
  "Integration flows for the User endpoints, through the real chain and a real
   PostgreSQL."
  (:require
   [borba.flow :as sf]
   [clojure.test :refer [is use-fixtures]]
   [com.borba.be-boilerplate.handlers.http.handlers]
   [flows.support :as support]
   [state-flow.api :as flow]))

(use-fixtures :once support/with-components)

(defn- unique-email
  []
  (str "andre+" (random-uuid) "@borba.com"))

(sf/defflow user-endpoints
  {:routes     [["/health"       :get  :health/check]
                ["/v1/users"     :post :user/create]
                ["/v1/users/:id" :get  :user/get-by-id]]
   :components (support/components)}

  ;; ── Health check ──────────────────────────────────────────────────────────
  [resp (sf/request :get "/health")]
  (flow/return
   (do (is (= 200 (:status resp)) "health: status 200")
       (is (= "UP" (:status (sf/json-body resp))) "health: status UP")))

  ;; ── Create user ───────────────────────────────────────────────────────────
  [email   (flow/return (unique-email))
   created (sf/request :post "/v1/users"
                       {:body {:name "André Borba" :email email}})]
  (flow/return
   (do (is (= 201 (:status created)) "create user: status 201")
       (is (some? (:id (sf/json-body created))) "create user: has id")))

  ;; ── Read it back ──────────────────────────────────────────────────────────
  [found (sf/request :get (str "/v1/users/" (:id (sf/json-body created))))]
  (flow/return
   (do (is (= 200 (:status found)) "get user: status 200")
       (is (= email (:email (sf/json-body found))) "get user: same email")))

  ;; ── Unknown and malformed ids ─────────────────────────────────────────────
  [missing (sf/request :get (str "/v1/users/" (random-uuid)))
   invalid (sf/request :get "/v1/users/42")]
  (flow/return
   (do (is (= 404 (:status missing)) "unknown user: status 404")
       (is (= 404 (:status invalid)) "malformed id: status 404")))

  ;; ── Invalid input ─────────────────────────────────────────────────────────
  [resp (sf/request :post "/v1/users" {:body {:name ""}})]
  (flow/return
   (is (= 422 (:status resp)) "create user invalid: status 422"))

  ;; ── Duplicate email ───────────────────────────────────────────────────────
  [resp (sf/request :post "/v1/users"
                    {:body {:name "Duplicate" :email email}})]
  (flow/return
   (do (is (= 409 (:status resp)) "duplicate email: status 409")
       (is (= "user/email-already-exists" (:error (sf/json-body resp)))
           "duplicate email: error code"))))
