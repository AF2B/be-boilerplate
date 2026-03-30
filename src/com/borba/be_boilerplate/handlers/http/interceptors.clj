(ns com.borba.be-boilerplate.handlers.http.interceptors
  "Service-specific Pedestal interceptors.

   Each interceptor is registered via defmethod so that borba-interceptors-component
   auto-discovers and assembles them into a map injected into the route component.

   ── Pattern ───────────────────────────────────────────────────────────────────

   1. Define a named function for the :enter and/or :leave stage.
   2. Register via defmethod interceptors/interceptor returning a map
      with :enter and/or :leave and/or :error.
   3. Reference it in a route tuple using its keyword:
        [\"/v1/users\" :post :user/create {:interceptors [:log-request :require-content-type]}]

   Interceptors run in the order listed (left → right) for :enter,
   and reverse order (right → left) for :leave.

   ── If the interceptor needs config ──────────────────────────────────────────

   Config arrives via the components map (second arg):

     (defmethod interceptors/interceptor :rate-limit [_ {:keys [redis]}]
       {:enter (fn [ctx] (check-rate! redis ctx))})"
  (:require [borba.interceptors.registry :as interceptors]
            [clojure.string :as str]))

;; ── :log-request ──────────────────────────────────────────────────────────────
;; Logs the incoming HTTP method and path before the handler runs.

(defn log-request-enter [ctx]
  (let [req    (:request ctx)
        method (str/upper-case (name (:request-method req)))
        path   (:path-info req)
        qs     (:query-string req)]
    (println (str "→ " method " " path
                  (when (seq qs) (str "?" qs)))))
  ctx)

(defmethod interceptors/interceptor :log-request [_ _]
  {:enter log-request-enter})

;; ── :require-content-type ─────────────────────────────────────────────────────
;; Validates that the request carries Content-Type: application/json.
;; Rejects requests that don't with 415 Unsupported Media Type.
;; Apply to routes that expect a JSON body (POST, PUT, PATCH).

(defn require-content-type-enter [ctx]
  (let [ct (get-in ctx [:request :headers "content-type"] "")]
    (if (str/includes? ct "application/json")
      ctx
      (throw (ex-info "Content-Type must be application/json"
                      {:status 415
                       :error  :unsupported-media-type})))))

(defmethod interceptors/interceptor :require-content-type [_ _]
  {:enter require-content-type-enter})
