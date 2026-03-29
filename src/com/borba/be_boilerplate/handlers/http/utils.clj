(ns com.borba.be-boilerplate.handlers.http.utils
  "HTTP utilities for be-boilerplate handlers.

   railway->response converts a Railway Either result into an HTTP
   response map with service-specific error → HTTP status mapping."
  (:require [com.borba.be-boilerplate.util.railway :as rop]))

(defn railway->response
  "Converts a Railway Either value into {:status N :body ...}.

   On Left  (error): maps :error keyword to an appropriate HTTP status.
   On Right (value): returns {:status status :body value}.

   Options:
     :status — success HTTP status code (default 200)

   Example:
     (utils/railway->response (user-biz/create! components body) :status 201)"
  [result & {:keys [status] :or {status 200}}]
  (rop/either result
              (fn [err]
                (let [http-status (case (:error err)
                                    :not-found            404
                                    :validation-failed    422
                                    :email-already-exists 409
                                    400)]
                  {:status http-status
                   :body   err}))
              (fn [val]
                {:status status
                 :body   val})))
