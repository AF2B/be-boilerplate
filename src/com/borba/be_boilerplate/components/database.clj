(ns com.borba.be-boilerplate.components.database
  "PostgreSQL component for be-boilerplate.

   The ig/init-key :components/database is registered by borba-sql-client-component.
   This namespace is kept as an alias so service code can still require it directly
   and get the full sql-client API.

   For direct SQL operations, prefer borba.sql-client functions:

     (require '[borba.sql-client :as sql])

     (sql/execute-one! sql-client [\"SELECT * FROM users WHERE id = ?\" id])
     (sql/find-one-by! sql-client :users {:email \"ana@borba.com\"})
     (sql/insert!      sql-client :users {:id ... :name ... :email ...})

   See borba-sql-client-component/src/borba/sql_client.clj for the full API."
  (:require [borba.sql-client]))
