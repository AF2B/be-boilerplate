(ns com.borba.be-boilerplate.handlers.http.handlers
  "HTTP handler registrations.

   Each handler follows the same pattern:
     1. A named function that receives the request map and returns a response.
     2. A thin defmethod that registers it under its route keyword.

   The named function is testable without a server. The defmethod is only the
   registration.

   ── Handler contract ──────────────────────────────────────────────────────

   Input (one map):
     {:keys [components body-params query-params path-params header-params
             request-id]}

   Output (HTTP response map):
     {:status 200 :body {...}}

   ── Adding a new handler ──────────────────────────────────────────────────

   1. Define the function:
        (defn create-payment [{:keys [components body-params]}]
          (rh/railway->response (payment/create! components body-params)
                                :status 201))

   2. Register it:
        (defmethod registry/handler :payment/create [_ _] create-payment)

   3. Add the route in system/stag.edn (and prod.edn, test.edn):
        [\"/v1/payments\" :post :payment/create]"
  (:require
   [borba.core.lifecycle :as lifecycle]
   [borba.handlers.registry :as registry]
   [borba.railway.http :as rh]
   [borba.sql-client :as sql]
   [com.borba.be-boilerplate.handlers.business.order :as order]
   [com.borba.be-boilerplate.handlers.business.user :as user]))

(def ^:private service-name "be-boilerplate")

(def ^:private status-ok 200)

(def ^:private status-created 201)

(def ^:private status-unavailable 503)

;; The errors of the domain that are not in borba.railway.http's defaults.
(def ^:private error-statuses
  {:user/email-already-exists 409})

;; ── Health ──────────────────────────────────────────────────────────────────

(defn health-check
  "Liveness: the process is up and answers.
   - _request: ignored"
  [_request]
  {:status status-ok
   :body   {:status "UP" :service service-name}})

(defmethod registry/handler :health/check [_ _] health-check)

(defn readiness
  "Readiness: the service takes traffic only when its lifecycle is :ready and
   the database answers. While it drains on shutdown this is a 503, which is
   how the load balancer learns to stop routing to it.
   - request: the request, with the :components of the service"
  [request]
  (let [{:keys [lifecycle sql-client]} (:components request)
        database? (sql/ready? sql-client)
        ready?    (and (lifecycle/ready? lifecycle) database?)]
    {:status (if ready? status-ok status-unavailable)
     :body   {:status   (lifecycle/state lifecycle)
              :database database?}}))

(defmethod registry/handler :health/ready [_ _] readiness)

;; ── User handlers ───────────────────────────────────────────────────────────

(defn create-user
  "Creates a user from the JSON body.
   - request: the request, with :components and :body-params"
  [request]
  (let [{:keys [components body-params]} request]
    (rh/railway->response (user/create! components body-params)
                          :status status-created
                          :error->status error-statuses)))

(defmethod registry/handler :user/create [_ _] create-user)

(defn get-user-by-id
  "Returns the user of the :id path parameter.
   - request: the request, with :components and :path-params"
  [request]
  (let [{:keys [components path-params]} request]
    (rh/railway->response (user/get-by-id components (:id path-params)))))

(defmethod registry/handler :user/get-by-id [_ _] get-user-by-id)

;; ── Order handlers ──────────────────────────────────────────────────────────

(defn create-order
  "Creates an order from the JSON body.
   - request: the request, with :components and :body-params"
  [request]
  (let [{:keys [components body-params]} request]
    (rh/railway->response (order/create! components body-params)
                          :status status-created)))

(defmethod registry/handler :order/create [_ _] create-order)

(defn get-order-by-id
  "Returns the order of the :id path parameter.
   - request: the request, with :components and :path-params"
  [request]
  (let [{:keys [components path-params]} request]
    (rh/railway->response (order/get-by-id components (:id path-params)))))

(defmethod registry/handler :order/get-by-id [_ _] get-order-by-id)
