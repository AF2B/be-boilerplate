# HOW TO USE — be-boilerplate

This document explains how everything fits together and how to do the most common tasks.
You do not need to read the source code. Read this, follow the steps, and you are productive.

---

## Table of Contents

1. [How it all works (the big picture)](#1-how-it-all-works)
2. [Starting the REPL and the server locally](#2-starting-the-repl-and-the-server-locally)
3. [Adding a new HTTP handler](#3-adding-a-new-http-handler)
4. [Adding a new interceptor](#4-adding-a-new-interceptor)
5. [Adding a new Kafka consumer](#5-adding-a-new-kafka-consumer)
6. [Using the Kafka producer](#6-using-the-kafka-producer)
7. [Adding a new database query](#7-adding-a-new-database-query)
8. [Adding a new external component](#8-adding-a-new-external-component)
9. [Railway (error handling)](#9-railway-error-handling)
10. [Running tests](#10-running-tests)
11. [Environment variables](#11-environment-variables)

---

## 1. How it all works

The architecture has three rules:

**Rule 1 — Config drives everything.**
There is no code that hardcodes "start this component" or "register this route".
All of that lives in `src/.../system/stag.edn` (staging) and `prod.edn` (production).
You add a route there. You add a topic there. The system reads the file and wires everything.

**Rule 2 — Register, don't import.**
Handlers and interceptors are registered via `defmethod`, not imported by a central file.
The framework discovers them automatically. You write the function, register it, declare the route — done.

**Rule 3 — Components are shared via injection.**
Every handler and interceptor receives a `components` map that contains the database connection,
Redis client, Kafka producer, and event store. You never instantiate these yourself.

### Startup sequence

```
borba.core.main/-main
  │
  ├── reads stag.edn (or prod.edn based on PROFILE env var)
  ├── requires all namespaces listed in :service/namespaces
  │     (this triggers all defmethod registrations)
  └── calls ig/init on the system map
        │
        ├── starts database, redis, kafka-producer, kafka-consumer, event-store
        ├── builds the interceptors map   (:service/interceptors)
        ├── builds the handlers map       (:service/handlers)
        ├── builds the routes table       (:http/routes)
        └── starts the HTTP server        (:server/http)
```

When the process receives SIGTERM/SIGINT, `ig/halt!` is called and everything shuts down cleanly.

---

## 2. Starting the REPL and the server locally

### Prerequisites

- Clojure CLI (`clj`) installed
- PostgreSQL, Redis, and Kafka running locally (or via Docker Compose)

### Start the REPL (for development)

In VSCode with Calva:
1. `Ctrl+Alt+C Ctrl+Alt+J` — Jack-In
2. Select `:dev` alias
3. The REPL opens. Your settings/configs are not touched.

In the terminal:
```bash
clj -M:dev
```

The `:dev` alias does NOT start the server. To start it manually from the REPL:
```clojure
(require '[borba.core.main])
(borba.core.main/-main "stag")
```

### Start the server directly

```bash
clj -M:run
# or with a specific profile:
PROFILE=prod clj -M:run
```

---

## 3. Adding a new HTTP handler

This is the most common task. There are three steps.

### Step 1 — Write the business logic

Create or open the file in `handlers/business/`. For example, `handlers/business/payment.clj`:

```clojure
(ns com.borba.be-boilerplate.handlers.business.payment
  (:require [com.borba.be-boilerplate.repository.payment :as repo]))

(defn create! [components body]
  ;; components has :sql-client, :redis, :kafka-producer, :event-store
  ;; Return Right on success, Left on failure — see section 9.
  (repo/insert! (:sql-client components) body))

(defn get-by-id [components id]
  (if-let [payment (repo/find-by-id (:sql-client components) id)]
    (borba.railway/right payment)
    (borba.railway/left {:error :not-found :message (str "Payment " id " not found")})))
```

### Step 2 — Register the HTTP handler

Open `handlers/http/handlers.clj` and add:

```clojure
(:require [...
           [com.borba.be-boilerplate.handlers.business.payment :as payment-biz]])

;; define the function
(defn create-payment [{:keys [components body-params]}]
  (rh/railway->response (payment-biz/create! components body-params) :status 201))

;; register it under a keyword
(defmethod handlers/handler :payment/create [_ _] create-payment)

(defn get-payment-by-id [{:keys [components path-params]}]
  (rh/railway->response (payment-biz/get-by-id components (:id path-params))))

(defmethod handlers/handler :payment/get-by-id [_ _] get-payment-by-id)
```

The handler function always receives a flat map with:
- `:components` — the shared components (db, redis, kafka, event-store)
- `:body-params` — parsed JSON body (POST/PUT/PATCH)
- `:path-params` — URL segments like `{:id "abc-123"}`
- `:query-params` — query string params
- `:header-params` — request headers

### Step 3 — Declare the route

Open `system/stag.edn` and add to `:http/routes :routes`:

```edn
["/v1/payments"      :post :payment/create
 {:interceptors [:log-request :require-content-type]}]

["/v1/payments/:id"  :get  :payment/get-by-id
 {:interceptors [:log-request]}]
```

Do the same in `system/prod.edn`.

That's it. No other file needs to change.

---

## 4. Adding a new interceptor

Interceptors run before (`:enter`) or after (`:leave`) the handler. Use them for cross-cutting concerns: auth, logging, rate limiting, content validation.

### Step 1 — Write the interceptor

Open `handlers/http/interceptors.clj` and add:

```clojure
;; The :enter function receives the Pedestal context map and must return it.
(defn auth-enter [ctx]
  (let [token (get-in ctx [:request :headers "authorization"])]
    (if (valid-token? token)
      ctx
      (throw (ex-info "Unauthorized" {:status 401 :error :unauthorized})))))

;; Register it. The keyword is how you reference it in routes.
(defmethod interceptors/interceptor :auth [_ _]
  {:enter auth-enter})
```

For a `:leave` interceptor (runs after the handler, e.g. to add response headers):

```clojure
(defmethod interceptors/interceptor :add-cors [_ _]
  {:leave (fn [ctx]
            (update-in ctx [:response :headers]
                       assoc "Access-Control-Allow-Origin" "*"))})
```

For both `:enter` and `:leave`:

```clojure
(defmethod interceptors/interceptor :trace [_ _]
  {:enter (fn [ctx] (println "→ entering") ctx)
   :leave (fn [ctx] (println "← leaving") ctx)})
```

If the interceptor needs a component (e.g. Redis for rate limiting), use the second argument:

```clojure
(defmethod interceptors/interceptor :rate-limit [_ {:keys [redis]}]
  {:enter (fn [ctx] (check-rate! redis ctx))})
```

### Step 2 — Reference it in the route

```edn
["/v1/payments" :post :payment/create
 {:interceptors [:auth :log-request :require-content-type]}]
```

Interceptors run **left to right** for `:enter`, **right to left** for `:leave`.

No other file needs to change. The component discovers all registered interceptors automatically.

---

## 5. Adding a new Kafka consumer

Consuming a topic requires three steps: write the handler, add the topic to the config, and map the topic to the handler.

### Step 1 — Write the handler

Open `events/consumer.clj`:

```clojure
(defn handle-payment-event [{:keys [key value topic offset]}]
  ;; value is the deserialized message (map)
  ;; This is a plain function — no Integrant, no framework.
  (println "Payment event received:" value))
```

The function receives a map with:
- `:key` — message key (string)
- `:value` — deserialized message body (map)
- `:topic` — topic name (string)
- `:offset` — partition offset (long)

### Step 2 — Add the topic and map the handler in stag.edn

```edn
:components/kafka-consumer
{:topics ["order-events" "payment-events"]}   ;; add the new topic

:kafka/consumer-handlers
{"order-events"   com.borba.be-boilerplate.events.consumer/handle-order-event
 "payment-events" com.borba.be-boilerplate.events.consumer/handle-payment-event}
```

Do the same in `prod.edn`.

The component will load the namespace and resolve the function at startup using `requiring-resolve`.
You do not need to add `events.consumer` to `:service/namespaces`.

---

## 6. Using the Kafka producer

The producer is available in every handler and interceptor via `components`.

```clojure
(defn create-payment [{:keys [components body-params]}]
  (let [result (payment-biz/create! components body-params)]
    ;; publish an event after creating
    (borba.kafka-producer/publish!
     (:kafka-producer components)
     "payment-events"          ;; topic
     (:id body-params)         ;; message key
     {:event "payment.created"
      :data  body-params})
    (rh/railway->response result :status 201)))
```

The producer handles serialization. Pass a plain Clojure map as the value.

---

## 7. Adding a new database query

Queries live in `repository/`. Create a new file or add to an existing one.

```clojure
(ns com.borba.be-boilerplate.repository.payment
  (:require [borba.sql-client :as sql]))

(defn insert! [sql-client payment]
  (sql/execute-one! sql-client
    ["INSERT INTO payments (id, amount, status) VALUES (?, ?, ?) RETURNING *"
     (:id payment) (:amount payment) "pending"]))

(defn find-by-id [sql-client id]
  (sql/execute-one! sql-client
    ["SELECT * FROM payments WHERE id = ?" id]))
```

`sql-client` comes from `components` in the handler:

```clojure
(repo/find-by-id (:sql-client components) id)
```

---

## 8. Adding a new external component

If you need to add a new infrastructure dependency (e.g. S3, Elasticsearch, a feature flag service):

### Step 1 — Add it to deps.edn

```edn
borba/borba-s3-component
{:git/url "https://gitlab.com/andre.borbaaf2b/borba-s3-component.git"
 :git/tag "v1.0.0"
 :git/sha "abc12345"}
```

### Step 2 — Add its namespace to :service/namespaces in base.edn

```edn
:service/namespaces
[...
 borba.s3]
```

### Step 3 — Declare its Integrant key in base.edn

```edn
:ig/system
{...
 :components/s3
 {:bucket #or [#env S3_BUCKET "my-bucket"]
  :region #or [#env AWS_REGION "us-east-1"]}}
```

### Step 4 — Add it to :components/all in base.edn

```edn
:components/all
{:sql-client     #ig/ref :components/database
 :redis          #ig/ref :components/redis
 :kafka-producer #ig/ref :components/kafka-producer
 :event-store    #ig/ref :components/event-store
 :s3             #ig/ref :components/s3}   ;; add this
```

Now every handler and interceptor receives `:s3` in the `components` map automatically.

---

## 9. Railway (error handling)

Every business function returns either a `Right` (success) or a `Left` (failure).
You never throw exceptions from business logic. You return `Left` with an error map.

```clojure
(require '[borba.railway :as r])

;; success
(r/right {:id "123" :amount 100})

;; failure
(r/left {:error :not-found :message "Payment not found"})
```

### Chaining operations

Use `r/>>= ` (bind) to chain operations that may fail.
If any step returns `Left`, the chain short-circuits.

```clojure
(r/>>= (find-user components id)
       (fn [user] (validate-balance user amount))
       (fn [user] (debit-account components user amount))
       (fn [_]    (r/right {:status "debited"})))
```

Or use the threading macro:

```clojure
(r/|> (find-user components id)
      #(validate-balance % amount)
      #(debit-account components % amount))
```

### Converting to HTTP response

`rh/railway->response` converts the result to a Pedestal response map automatically:

```clojure
(rh/railway->response result)            ;; 200 on Right, mapped status on Left
(rh/railway->response result :status 201) ;; 201 on Right
```

Default error → HTTP status mappings:

| `:error` keyword     | HTTP status |
|----------------------|-------------|
| `:not-found`         | 404         |
| `:validation-failed` | 422         |
| `:conflict`          | 409         |
| `:unauthorized`      | 401         |
| `:forbidden`         | 403         |
| anything else        | 500         |

---

## 10. Running tests

```bash
# run all tests
clj -M:test

# run a specific namespace
clj -M:test --focus com.borba.be-boilerplate.handlers.business.user-test
```

Integration tests use `nubank/state-flow`. They require the system to be running.
Unit tests are plain `clojure.test` — no system needed.

---

## 11. Environment variables

| Variable             | Default                                              | Description              |
|----------------------|------------------------------------------------------|--------------------------|
| `PROFILE`            | `stag`                                               | Which EDN profile to load (`stag` or `prod`) |
| `PORT`               | `8080`                                               | HTTP server port         |
| `APP_VERSION`        | `dev`                                                | Shown in startup banner  |
| `DATABASE_URL`       | `jdbc:postgresql://localhost:5432/be_boilerplate_dev`| PostgreSQL JDBC URL      |
| `DB_USER`            | `borba`                                              | Database username        |
| `DB_PASS`            | `borba_secret`                                       | Database password        |
| `REDIS_HOST`         | `localhost`                                          | Redis host               |
| `REDIS_PORT`         | `6379`                                               | Redis port               |
| `KAFKA_BROKERS`      | `localhost:9092`                                     | Kafka bootstrap servers  |
| `SCHEMA_REGISTRY_URL`| `http://localhost:8081`                              | Confluent Schema Registry|

All variables have sensible local defaults. You only need to set them in production.
