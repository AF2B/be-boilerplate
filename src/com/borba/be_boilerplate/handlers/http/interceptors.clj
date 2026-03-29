(ns com.borba.be-boilerplate.handlers.http.interceptors
  "Service-specific HTTP utilities.

   Infrastructure interceptors (inject-components, parse-body, parse-query,
   parse-path-params, parse-headers, json-response, error-handler) are now
   provided by borba-handlers-component (borba.handlers.interceptors).

   This namespace is kept for any interceptors unique to this service.
   Custom interceptors can be registered in routes.clj via defmethod:

     (defmethod borba.handlers.registry/interceptor :auth/check [_ components]
       {:name  :auth/check
        :enter (fn [ctx]
                 (let [token (get-in ctx [:request :headers-map \"authorization\"])]
                   (if (valid-token? token)
                     ctx
                     (throw (ex-info \"Unauthorized\" {:status 401 :error :unauthorized})))))})")
