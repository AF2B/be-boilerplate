(ns com.borba.be-boilerplate.handlers.http.handlers
  "HTTP handler registrations.

   Each handler follows the same pattern:
     1. A named function that receives the flat request map and returns a response map.
     2. A thin defmethod that registers the function under its route keyword.

   The named function is independently testable. The defmethod is just registration.

   ── Handler contract ──────────────────────────────────────────────────────────

   Input (flat map):
     {:keys [components body-params query-params path-params header-params]}

   Output (HTTP response map):
     {:status 200 :body {...}}

   ── Adding a new handler ──────────────────────────────────────────────────────

   1. Define the function:
        (defn create-payment [{:keys [components body-params]}]
          (rh/railway->response (payment-biz/create! components body-params) :status 201))

   2. Register it:
        (defmethod handlers/handler :payment/create [_ _] create-payment)

   3. Add the route in system/stag.edn (and prod.edn):
        [\"/v1/payments\" :post :payment/create]"
  (:require [borba.handlers.registry :as handlers]
            [borba.railway.http :as rh]
            [com.borba.be-boilerplate.handlers.business.user :as user-biz]
            [com.borba.be-boilerplate.handlers.business.order :as order-biz]))

;; ── Health ────────────────────────────────────────────────────────────────────

(defn health-check [_]
  {:status 200
   :body   {:status "UP" :service "be-boilerplate"}})

(defmethod handlers/handler :health/check [_ _] health-check)

;; ── User handlers ─────────────────────────────────────────────────────────────

(defn create-user [{:keys [components body-params]}]
  (rh/railway->response (user-biz/create! components body-params) :status 201))

(defmethod handlers/handler :user/create [_ _] create-user)

(defn get-user-by-id [{:keys [components path-params]}]
  (rh/railway->response (user-biz/get-by-id components (:id path-params))))

(defmethod handlers/handler :user/get-by-id [_ _] get-user-by-id)

;; ── Order handlers ────────────────────────────────────────────────────────────

(defn create-order [{:keys [components body-params]}]
  (rh/railway->response (order-biz/create! components body-params) :status 201))

(defmethod handlers/handler :order/create [_ _] create-order)

(defn get-order-by-id [{:keys [components path-params]}]
  (rh/railway->response (order-biz/get-by-id components (:id path-params))))

(defmethod handlers/handler :order/get-by-id [_ _] get-order-by-id)
