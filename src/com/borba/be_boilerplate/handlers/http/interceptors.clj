(ns com.borba.be-boilerplate.handlers.http.interceptors
  "Service-specific Pedestal interceptors.

   Each interceptor is registered via defmethod, so that
   borba-interceptors-component builds it into the map the routes and the
   handlers look interceptors up in.

   ── Pattern ───────────────────────────────────────────────────────────────

   1. Define a named function for the :enter and/or :leave stage.
   2. Register it with a defmethod that returns a map with :enter, :leave
      and/or :error.
   3. Reference it in a route by its keyword:
        [\"/v1/users\" :post :user/create
         {:interceptors [:log-request :require-content-type]}]

   Route interceptors run after the chain every handler has (request id,
   access log, error handler, parsers) and before the handler, in the order
   listed for :enter and in reverse for :leave.

   ── If the interceptor needs a component ─────────────────────────────────

   The components arrive as the second argument of the method:

     (defmethod registry/interceptor :rate-limit [_ {:keys [redis]}]
       {:enter (fn [ctx] (check-rate! redis ctx))})"
  (:require
   [borba.interceptors.registry :as registry]
   [clojure.string :as str]
   [clojure.tools.logging :as log]))

(def ^:private json-media-type "application/json")

(def ^:private status-unsupported-media-type 415)

;; ── :log-request ────────────────────────────────────────────────────────────
;; Logs the method and the path before the handler runs. The access log of
;; the handlers chain already logs every request with its status and time;
;; this one shows the shape of a route-level interceptor.

(defn log-request-enter
  "Logs the method and the path of the request, and returns the context.
   - ctx: the Pedestal context"
  [ctx]
  (let [{:keys [request-method path-info]} (:request ctx)]
    (log/debug "handling" (str/upper-case (name request-method)) path-info))
  ctx)

(defmethod registry/interceptor :log-request [_ _]
  {:enter log-request-enter})

;; ── :require-content-type ───────────────────────────────────────────────────
;; Rejects a request without Content-Type: application/json with a 415, even
;; when it has no body (the chain of the handlers checks it only for a body).
;; Apply it to routes that expect a JSON body (POST, PUT, PATCH).

(defn require-content-type-enter
  "Returns the context when the request is JSON, and throws the 415 that the
   error handler of the chain turns into a typed response otherwise.
   - ctx: the Pedestal context"
  [ctx]
  (let [content-type (get-in ctx [:request :headers "content-type"] "")]
    (if (str/includes? content-type json-media-type)
      ctx
      (throw (ex-info "Content-Type must be application/json"
                      {:status status-unsupported-media-type
                       :error  :unsupported-media-type})))))

(defmethod registry/interceptor :require-content-type [_ _]
  {:enter require-content-type-enter})
