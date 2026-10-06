(ns com.borba.be-boilerplate.system.projection
  (:require [integrant.core :as ig]))

(defmethod ig/init-key :projection/folding-funcs
  [_key _settings]
  {})
