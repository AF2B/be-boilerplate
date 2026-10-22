# HOW TO USE — be-boilerplate

This document explains how everything fits together and how to do the most common tasks.
Read this, follow the steps, and you are productive.

---

## Table of Contents

1. [How it all works](#1-how-it-all-works)
2. [Starting the REPL and the server locally](#2-starting-the-repl-and-the-server-locally)
3. [Adding a new HTTP handler](#3-adding-a-new-http-handler)
4. [Adding a new interceptor](#4-adding-a-new-interceptor)
5. [Adding a new Kafka consumer](#5-adding-a-new-kafka-consumer)
6. [Using the Kafka producer](#6-using-the-kafka-producer)
7. [Adding a new database query](#7-adding-a-new-database-query)
8. [Adding a new external component](#8-adding-a-new-external-component)
9. [Railway (error handling)](#9-railway-error-handling)
10. [Health, readiness and shutdown](#10-health-readiness-and-shutdown)
11. [Running tests](#11-running-tests)
12. [Environment variables](#12-environment-variables)

---

## 1. How it all works

The architecture has three rules:

**Rule 1 — Config drives everything.**
No code hardcodes "start this component" or "register this route". The system is described in
`src/com/borba/be_boilerplate/system/base.edn`, and each profile (`stag.edn`, `prod.edn`, `test.edn`)
deep-merges its differences on top. You add a route there. You add a topic there.

**Rule 2 — Register, don't import.**
Handlers and interceptors are registered with `defmethod`, not imported by a central file. You write
the function, register it, declare the route — done.

**Rule 3 — Components are shared via injection.**
Every handler and interceptor receives the `:service/components` map: the database, the event
store, Redis, the Kafka producer and the lifecycle. You never instantiate these yourself.

### Startup sequence

```
borba.core.main/-main <profile>         (or the PROFILE variable; there is no default)
  │
  ├── reads config.edn for the profile   (system/<profile>.edn = base.edn + overrides)
  ├── requires every namespace in :service/namespaces
  │     (this loads every defmethod registration)
  └── ig/init on :ig/system
        │
        ├── starts the database, the event store, Redis, the Kafka producer
        ├── builds :service/components, :service/interceptors, :service/handlers
        ├── builds the routes (:http/routes) and starts the server (:server/http)
        ├── :borba/core marks the lifecycle :ready
        └── starts the Kafka consumer
```

A component that cannot start (a database that does not answer, brokers that are away) fails the
start, halts what had started, and the process exits with status 1. Without a profile it exits with 2.

---

## 2. Starting the REPL and the server locally

### Prerequisites

- JDK 21 and the Clojure CLI
- Docker, for PostgreSQL, Redis and Kafka

### Configure and start the infrastructure

```bash
cp .env.example .env      # local values, never committed
make docker-up            # PostgreSQL, Redis, Kafka (KRaft) and the Schema Registry
```

`make` and `docker compose` both read `.env`. If a port is taken on your machine, change it there
(`POSTGRES_PORT` and the port in `DATABASE_URL`, `REDIS_PORT`, `KAFKA_PORT` and `KAFKA_BROKERS`,
`SCHEMA_REGISTRY_PORT` and `SCHEMA_REGISTRY_URL`).

### Start the server

```bash
make run                  # profile stag
make run PROFILE=prod     # any other profile
```

`stag` is the default only for `make`. The image and the orchestrator always set `PROFILE`
themselves, so a container without it exits instead of starting with the wrong configuration.

### Start the REPL

With the variables of `.env` in the environment (`set -a; . ./.env; set +a`):

```bash
clojure -M:dev
```

In VS Code with Calva, Jack-In with the `:dev` alias. Then:

```clojure
(go)          ;; starts the service with :stag, from dev/user.clj
(go :prod)    ;; or with another profile
(halt)        ;; stops it
(running)     ;; the running system
```

---

## 3. Adding a new HTTP handler

This is the most common task. There are three steps.

### Step 1 — Write the business logic

Create `handlers/business/payment.clj`. Every step returns a `Right` or a `Left` (section 9):

```clojure
(ns com.borba.be-boilerplate.handlers.business.payment
  (:require
   [borba.railway :as rop]
   [com.borba.be-boilerplate.repository.payment :as repo]))

(defn get-by-id
  "Returns Right{payment} or Left{:error :not-found}.
   - components: the service components
   - id: the id of the payment, as text"
  [components id]
  (if-let [payment (some->> (parse-uuid id) (repo/find-by-id components))]
    (rop/right payment)
    (rop/failure :not-found {:id id})))
```

### Step 2 — Register the HTTP handler

Open `handlers/http/handlers.clj` and add:

```clojure
(defn get-payment-by-id
  "Returns the payment of the :id path parameter.
   - request: the request, with :components and :path-params"
  [request]
  (let [{:keys [components path-params]} request]
    (rh/railway->response (payment/get-by-id components (:id path-params)))))

(defmethod registry/handler :payment/get-by-id [_ _] get-payment-by-id)
```

The handler function receives one map with:
- `:components` — the shared components
- `:body-params` — the JSON body, read strictly, with keyword keys (`{}` without a body)
- `:path-params` — URL segments like `{:id "abc-123"}`
- `:query-params` — the query string
- `:header-params` — the headers, lower-case
- `:request-id` — the id of the request, also in the logs and in `X-Request-Id`

A body that is not JSON is a 400, one without `Content-Type: application/json` a 415, one over
1 MiB a 413. Every error body is `{"error": ..., "message": ..., "request-id": ...}`.

### Step 3 — Declare the route

Add it to `:http/routes :routes` in `system/stag.edn`, `prod.edn` and `test.edn`:

```edn
["/v1/payments/:id" :get :payment/get-by-id
 {:interceptors [:log-request]}]
```

A route that names a handler or an interceptor that is not registered fails the start, naming it.
A handler used by two routes needs a `:name` on each.

---

## 4. Adding a new interceptor

Interceptors run before (`:enter`) or after (`:leave`) the handler: auth, rate limiting, headers.
Every handler already has a request id, an access log, an error handler and the parsers; route
interceptors run after those and before the handler.

### Step 1 — Write the interceptor

Open `handlers/http/interceptors.clj` and add:

```clojure
(defn auth-enter
  "Returns the context when the token is valid, and throws a 401 otherwise.
   - ctx: the Pedestal context"
  [ctx]
  (let [token (get-in ctx [:request :headers "authorization"])]
    (if (valid-token? token)
      ctx
      (throw (ex-info "Unauthorized" {:status 401 :error :unauthorized})))))

(defmethod registry/interceptor :auth [_ _]
  {:enter auth-enter})
```

An `ex-info` with a `:status` becomes that response, with its `:error` code; any other exception is
a 500 that says nothing to the client and is logged with the request id.

If the interceptor needs a component, it is in the second argument:

```clojure
(defmethod registry/interceptor :rate-limit [_ {:keys [redis]}]
  {:enter (fn [ctx] (check-rate! redis ctx))})
```

### Step 2 — Reference it in the route

```edn
["/v1/payments" :post :payment/create
 {:interceptors [:auth :log-request :require-content-type]}]
```

Interceptors run **left to right** for `:enter`, **right to left** for `:leave`.

---

## 5. Adding a new Kafka consumer

### Step 1 — Write the handler

Open `events/consumer.clj`:

```clojure
(defn handle-payment-event
  "Handles a message of the payment-events topic.
   - message: the message, a map of :key, :value, :topic and :offset"
  [message]
  (let [{:keys [key value]} message]
    (payments/apply-event! key value)))
```

The message is a map of `:key` (text), `:value` (the JSON, with keyword keys, already validated
against the schema it was written with), `:topic`,
`:partition`, `:offset`, `:timestamp` and `:headers`.

Delivery is **at-least-once**: the offset is committed after the handler returns, so a message can
be delivered twice. Make the handler safe to run twice (an upsert, a check of what was done). A
handler that throws is tried again (`:max-attempts`, 3 by default) and then reported to `:on-error`
or logged. A value that its schema refuses (`:schema-violation`) or that is not in the wire format
(`:invalid-value`) is reported the same way; while the registry does not answer, the message is
read again and never skipped.

### Step 2 — Add the topic and map the handler

In `stag.edn` and `prod.edn`:

```edn
:components/kafka-consumer
{:topics ["order-events" "payment-events"]}

:kafka/consumer-handlers
{"order-events"   com.borba.be-boilerplate.events.consumer/handle-order-event
 "payment-events" com.borba.be-boilerplate.events.consumer/handle-payment-event}
```

The component loads the namespace of the symbol, so `events.consumer` is not in
`:service/namespaces`. A topic without a handler fails the start.

---

## 6. Using the Kafka producer

Every topic the service writes has a **JSON Schema** held by the Schema Registry: the contract of
the topic. The producer checks every value against it before sending (a value it refuses is not
sent, and `send!` throws `:schema-violation`), writes it in the wire format of Confluent, and the
consumer reads it with the same schema. A topic without a schema cannot be written to.

### Step 1 — Write the schema

Create `events/schemas/payment_events.edn`, a JSON Schema as EDN (see `order_events.edn`):

```edn
{:type                 "object"
 :required             ["event-type" "data"]
 :additionalProperties false
 :properties           {:event-type {:type "string" :enum ["payment-created"]}
                        :data       {:type "object"}}}
```

### Step 2 — Declare it in base.edn

```edn
:components/kafka-producer
{...
 :schema-registry
 {:url     #env SCHEMA_REGISTRY_URL
  :schemas {"order-events"
            #include "com/borba/be_boilerplate/events/schemas/order_events.edn"
            "payment-events"
            #include "com/borba/be_boilerplate/events/schemas/payment_events.edn"}}}
```

### Step 3 — Register it

- **Locally (`stag`)** the producer registers its schemas when it starts (`:register? true`). The
  registry refuses a schema that is not compatible with the versions before it (`BACKWARD` by
  default), and the start fails with `incompatible-schema`.
- **Elsewhere (`prod`)** the start only checks that each schema is registered, and fails with
  `schema-not-registered` otherwise: the contract changes when someone registers it, before the
  deploy, never as a side effect of one. Register with the REST API of the registry:

```bash
jq -n --arg schema "$(clojure -M -e '(require (quote [clojure.edn :as edn]) (quote [jsonista.core :as j])) (print (j/write-value-as-string (edn/read-string (slurp "src/com/borba/be_boilerplate/events/schemas/payment_events.edn"))))')" \
  '{schemaType: "JSON", schema: $schema}' |
  curl -X POST -H "Content-Type: application/vnd.schemaregistry.v1+json" \
       --data @- "$SCHEMA_REGISTRY_URL/subjects/payment-events-value/versions"
```

### Step 4 — Publish

Wrap each event in a domain-named function in `events/producer.clj`:

```clojure
(defn payment-created!
  "Publishes a payment-created event.
   - kafka-producer: the kafka-producer component
   - payment: the payment as it was stored"
  [kafka-producer payment]
  (kafka/send! kafka-producer
               payment-events-topic
               (:id payment)
               {:event-type "payment-created"
                :data       payment}))
```

- The value is sent as JSON; the key keeps the events of an aggregate in order.
- `kafka/send!` is asynchronous and returns a future; `kafka/send-sync!` waits and returns
  `{:topic :partition :offset :timestamp}`. A failure that nobody waits for is logged.
- Delivery is `acks=all` with idempotence: a retry is not a duplicate.

See `handlers/business/order.clj`: the order and its event are stored in one transaction, and the
event is published after it, without failing the request when Kafka is away.

---

## 7. Adding a new database query

Queries live in `repository/` and take the components:

```clojure
(ns com.borba.be-boilerplate.repository.payment
  (:require
   [borba.sql-client :as sql]))

(defn insert!
  "Inserts a payment and returns the stored row.
   - components: the service components, with :sql-client
   - payment: a map of :id, :amount and :status"
  [components payment]
  (sql/insert! (:sql-client components) :payments payment))

(defn find-by-id
  "Returns the payment with an id, or nil.
   - components: the service components, with :sql-client
   - id: the UUID of the payment"
  [components id]
  (sql/find-one-by! (:sql-client components) :payments {:id id}))
```

- Rows come back with kebab-case keys (`created_at` is `:created-at`); timestamps are `Instant`.
- `insert!`, `update!`, `delete!`, `find-by!` and `find-one-by!` write the table and column names
  themselves and refuse an empty condition. For anything else, `sql/execute!` with the SQL written out.
- `sql/transact!` runs a function in a transaction; the connection it gives takes the place of the
  data source (`(assoc components :sql-client tx)`).
- A database failure is classified by `sql/error-data`: `:unique-violation`, `:foreign-key-violation`,
  ... (see `handlers/business/user.clj`).

Schema changes go in a new file of `db/migrations/` (`002_payments.sql`), applied in order by the
local container on its first start and by the pipeline before the integration tests. On an existing
local volume, apply it by hand or run `make docker-down && make docker-up`.

---

## 8. Adding a new external component

### Step 1 — Add it to deps.edn

```edn
io.github.af2b/borba-s3-component
{:git/url "https://github.com/AF2B/borba-s3-component"
 :git/tag "v1.0.0"
 :git/sha "<the commit of the tag, printed in the release notes>"}
```

### Step 2 — Add its namespace to `:service/namespaces` in base.edn

```edn
:service/namespaces
[...
 borba.s3]
```

### Step 3 — Declare its Integrant key in base.edn

```edn
:components/s3
{:bucket #env S3_BUCKET
 :region #or [#env AWS_REGION "us-east-1"]}
```

### Step 4 — Hand it to the handlers in `:service/components`

```edn
:service/components
{:lifecycle      #ig/ref :borba/lifecycle
 :sql-client     #ig/ref :components/database
 ...
 :s3             #ig/ref :components/s3}
```

Now every handler and interceptor receives `:s3` in its components. A key whose namespace is not
loaded fails the start with `no component is registered for :components/s3`.

---

## 9. Railway (error handling)

Business functions return either a `Right` (success) or a `Left` (failure). An expected failure is
data, a map with an `:error` code; exceptions are for bugs and for the edges (I/O).

```clojure
(require '[borba.railway :as rop])

(rop/right {:id "123" :amount 100})
(rop/failure :not-found {:id "123"})   ;; => Left{:error :not-found :id "123"}
```

### Chaining operations

`rop/|>` threads a value through steps and stops at the first `Left`. A step with its own arguments
is a call that returns the step:

```clojure
(rop/|> input
        validate-create-input
        (check-email-unique components)
        enrich-user
        (persist-user components))
```

A step must return a `Right` or a `Left`: anything else throws. `rop/try-right` turns a function that
throws into a `Left` (the message of the exception is withheld; an `ex-info` with `:error` is kept).

### Converting to an HTTP response

```clojure
(rh/railway->response result)                    ;; 200 on Right
(rh/railway->response result :status 201)        ;; 201 on Right
(rh/railway->response result
                      :error->status {:user/email-already-exists 409})
```

| `:error`                 | HTTP status |
|--------------------------|-------------|
| `:not-found`             | 404         |
| `:validation-failed`     | 422         |
| `:conflict`              | 409         |
| `:unauthorized`          | 401         |
| `:forbidden`             | 403         |
| `:unsupported-media-type`| 415         |
| `:exception`             | 500         |
| anything else            | 400         |

---

## 10. Health, readiness and shutdown

| Route     | What it says |
|-----------|--------------|
| `/health` | Liveness: the process answers. Used by the Docker `HEALTHCHECK`. |
| `/readyz` | Readiness: `200` only when the lifecycle is `:ready` and the database answers; `503` while starting, draining or without the database. |

On `SIGTERM` the service drains: `:borba/core` turns readiness off (`/readyz` becomes `503
draining`), waits `:drain-delay-ms` while the server still serves, and only then stops the server,
the consumer and the rest. `prod` waits 15 s: set it to at least the readiness `periodSeconds` times
its `failureThreshold`, and keep the orchestrator's grace period longer than the drain plus the stop.

---

## 11. Running tests

```bash
make test                 # unit tests, anywhere
make test-integration     # integration flows, against PostgreSQL (make docker-up)
make coverage             # unit tests with coverage, target/coverage/
make lint                 # clj-kondo, fails on any warning
make fmt-check            # cljfmt
```

- **Unit tests** are plain `clojure.test` and `test.check` properties, with generators in
  `test/generators/`.
- **Integration flows** (`test/flows/`) use `borba-state-flow-component`: real handlers, interceptors
  and routes, in process, with no port, against a real PostgreSQL. Their namespace carries
  `^:integration`; `flows.support` starts the database and the event store of the `test` profile.

---

## 12. Environment variables

| Variable            | Used by                    | Local value (`.env.example`)                     |
|---------------------|----------------------------|--------------------------------------------------|
| `PROFILE`           | the service (or argument)  | set by `make run` (`stag`)                       |
| `PORT`              | HTTP server                | `8080`                                           |
| `APP_VERSION`       | startup log                | `dev` when unset                                 |
| `DATABASE_URL`      | PostgreSQL JDBC URL        | `jdbc:postgresql://localhost:5432/be_boilerplate`|
| `DATABASE_USER`     | PostgreSQL user            | `be_boilerplate`                                 |
| `DATABASE_PASSWORD` | PostgreSQL password        | a local placeholder                              |
| `REDIS_HOST`        | Redis host                 | `localhost`                                      |
| `REDIS_PORT`        | Redis port                 | `6379`                                           |
| `REDIS_PASSWORD`    | Redis password             | unset                                            |
| `KAFKA_BROKERS`     | Kafka bootstrap servers    | `localhost:9092`                                 |
| `SCHEMA_REGISTRY_URL` | Schema Registry          | `http://localhost:8081`                          |
| `POSTGRES_DB`, `POSTGRES_PORT`, `KAFKA_PORT`, `SCHEMA_REGISTRY_PORT` | `docker compose` only | `be_boilerplate`, `5432`, `9092`, `8081` |

The database credentials have no default in the configuration: outside a laptop they come from the
orchestrator, and a service without them does not start.
