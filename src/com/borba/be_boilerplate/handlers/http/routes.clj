(ns com.borba.be-boilerplate.handlers.http.routes
  "Route handler registrations for be-boilerplate.

   Each handler is registered via defmethod borba.handlers.registry/handler.
   The borba-handlers-component (:service/handlers) auto-discovers all
   registered handlers and builds their Pedestal interceptor chains.

   ── Handler contract ─────────────────────────────────────────────────────────

   Every handler function receives a single flat map:

     {:keys [components body-params query-params path-params header-params]}

   And must return an HTTP response map:

     {:status 200 :body {...}}

   ── Extra interceptors ───────────────────────────────────────────────────────

   To attach custom interceptors to a specific route, override
   handler-interceptors in this namespace:

     (defmethod handlers/handler-interceptors :admin/dashboard [_]
       [:auth/admin-check])

   Then register the interceptor:

     (defmethod handlers/interceptor :auth/admin-check [_ components]
       {:name  :auth/admin-check
        :enter (fn [ctx] ...)})

   ── Adding a new route ───────────────────────────────────────────────────────

   1. Add the route to system/stag.edn (and prod.edn if different):
        [\"/v1/payments\" :post :payment/create]

   2. Register the handler here:
        (defmethod handlers/handler :payment/create [_ _]
          payment-create-handler)

   3. Implement the handler fn:
        (defn payment-create-handler [{:keys [components body-params]}]
          {:status 201 :body (payment-biz/create! components body-params)})"
  (:require [borba.handlers.registry :as handlers]
            [com.borba.be-boilerplate.handlers.http.utils :as utils]
            [com.borba.be-boilerplate.handlers.business.user :as user-biz]
            [com.borba.be-boilerplate.handlers.business.order :as order-biz]))

;; ── Health ────────────────────────────────────────────────────────────────────

(defmethod handlers/handler :health/check [_ _]
  (fn [_]
    {:status 200
     :body   {:status "UP" :service "be-boilerplate"}}))

;; ── User handlers ────────────────────────────────────────────────────────────

(defmethod handlers/handler :user/create [_ _]
  (fn [{:keys [components body-params]}]
    (utils/railway->response (user-biz/create! components body-params) :status 201)))

(defmethod handlers/handler :user/get-by-id [_ _]
  (fn [{:keys [components path-params]}]
    (utils/railway->response (user-biz/get-by-id components (:id path-params)))))

;; ── Order handlers ────────────────────────────────────────────────────────────

(defmethod handlers/handler :order/create [_ _]
  (fn [{:keys [components body-params]}]
    (utils/railway->response (order-biz/create! components body-params) :status 201)))

(defmethod handlers/handler :order/get-by-id [_ _]
  (fn [{:keys [components path-params]}]
    (utils/railway->response (order-biz/get-by-id components (:id path-params)))))
