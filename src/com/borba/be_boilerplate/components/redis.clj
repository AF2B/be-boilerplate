(ns com.borba.be-boilerplate.components.redis
  "Redis component for be-boilerplate.

   The ig/init-key :components/redis is registered by borba-redis-component.
   This namespace is kept as an alias so service code can still require it directly
   and get the full redis API.

   For Redis operations, prefer borba.redis functions:

     (require '[borba.redis :as redis])

     (redis/get!  conn \"session:abc\")
     (redis/set!  conn \"session:abc\" token {:ttl-seconds 3600})
     (redis/del!  conn \"session:abc\")
     (redis/hset! conn \"user:1\" {:name \"Ana\"})

   See borba-redis-component/src/borba/redis.clj for the full API."
  (:require [borba.redis]))
