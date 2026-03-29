(ns com.borba.be-boilerplate.core
  "Core namespace — loaded at system startup via :service/core-ns.

   Requires every namespace that registers Integrant defmethod or
   borba.handlers.registry defmethod so they are all available when
   the system config is read and initialised.

   ── External Borba components ────────────────────────────────────────────────
   Each require below loads an ig/init-key registration from its component.
   These are local packages (see deps.edn) and will become separate git repos.

   ── Service-specific namespaces ──────────────────────────────────────────────
   Routes register handlers via defmethod borba.handlers.registry/handler.
   Events register Kafka consumer handlers via defmethod ig/init-key."
  (:require
   ;; ── Borba infrastructure components ─────────────────────────────────────
   [borba.core]                          ; startup banner + lifecycle logging
   [borba.sql-client]                    ; :components/database (PostgreSQL + HikariCP)
   [borba.redis]                         ; :components/redis (Carmine)
   [borba.kafka-producer]                ; :components/kafka-producer
   [borba.kafka-consumer]                ; :components/kafka-consumer + :kafka/consumer-handlers
   [borba.event-store]                   ; :components/event-store
   [borba.handlers.registry]             ; defmulti handler / interceptor / handler-interceptors
   [borba.handlers.component]            ; :service/handlers (auto-discovers registered handlers)

   ;; ── Borba platform components (external git repos) ───────────────────────
   [borba.routes.component]              ; :http/routes (Pedestal route table)
   [borba.server.component]              ; :server/http (Jetty)

   ;; ── Service: system ──────────────────────────────────────────────────────
   [com.borba.be-boilerplate.system.projection] ; :projection/folding-funcs

   ;; ── Service: route handlers ──────────────────────────────────────────────
   ;; Registers defmethod borba.handlers.registry/handler for each route keyword
   [com.borba.be-boilerplate.handlers.http.routes]

   ;; ── Service: Kafka consumer handlers ────────────────────────────────────
   ;; Overrides defmethod ig/init-key :kafka/consumer-handlers with topic→fn map
   [com.borba.be-boilerplate.events.consumer]))
