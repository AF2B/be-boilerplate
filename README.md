# be-boilerplate

> Clojure microservice boilerplate — Data-Driven, Event-Sourced, Railway-Oriented.

## Stack

| Layer              | Tech                                     |
|--------------------|------------------------------------------|
| Language           | Clojure 1.12                             |
| HTTP               | Pedestal + Jetty                         |
| Config             | Aero + Integrant (Data-Driven EDN)       |
| Validation         | spec.alpha                               |
| Database           | PostgreSQL 16 (next.jdbc + HikariCP)     |
| Cache              | Redis (Carmine)                          |
| Messaging          | Kafka + Schema Registry                  |
| Error Handling     | Railway Oriented Programming (Either)    |
| Event Sourcing     | PostgreSQL-backed Event Store            |
| Testing            | clojure.test + state-flow + test.check   |

## Quick Start

```bash
# Start infrastructure
make docker-up

# Install dependencies
make deps

# Run (staging profile)
make run

# Run tests
make test

# Generate a new microservice
make init NAME=payment-service
```

## Project Structure

```
src/
├── config.edn                              # Profile router (→ system/*.edn)
└── com/borba/be_boilerplate/
    ├── core.clj                            # Namespace loader (ig/init-key registry)
    ├── components/                         # Component injection EDN + init namespaces
    │   ├── base.edn                        # Base component map (:sql-client, :redis, etc.)
    │   ├── stag.edn / prod.edn / test.edn # Per-profile component overrides
    │   ├── database.clj                    # PostgreSQL (next.jdbc + HikariCP)
    │   ├── redis.clj                       # Redis (Carmine)
    │   ├── kafka_producer.clj              # Kafka producer
    │   ├── kafka_consumer.clj              # Kafka consumer
    │   └── event_store.clj                 # Event Store (PostgreSQL)
    ├── system/                             # System config EDN
    │   ├── base.edn                        # Integrant system keys (server, routes, DB, etc.)
    │   ├── stag.edn / prod.edn / test.edn # Per-profile route + config overrides
    │   └── projection.clj                  # Event projections
    ├── handlers/
    │   ├── http/
    │   │   ├── interceptors.clj            # Pedestal interceptors (components)
    │   │   └── routes.clj                  # defmethod ig/init-key :service/handlers
    │   └── business/
    │       ├── user.clj                    # User business logic (Railway)
    │       └── order.clj                   # Order business logic (Railway + Events)
    ├── repository/
    │   ├── user.clj                        # User DB queries
    │   └── order.clj                       # Order DB queries
    ├── specs/
    │   ├── user.clj                        # User specs
    │   └── order.clj                       # Order specs
    ├── events/
    │   ├── producer.clj                    # Domain event publishers
    │   └── consumer.clj                    # Kafka consumer handlers
    └── util/
        ├── railway.clj                     # Either monad (ROP)
        ├── predicates.clj                  # Common predicates
        └── validation.clj                  # Spec validation helpers
```

## Architecture

### Component Injection

Every layer receives external dependencies through the `:components` map:

```clojure
;; In repository
(defn find-by-id [components id]
  (let [{:keys [sql-client]} components]
    (jdbc/execute-one! sql-client ["SELECT * FROM users WHERE id = ?" id])))
```

### Railway Oriented Programming

Business functions return `Right` (success) or `Left` (failure):

```clojure
(rop/|> input
    validate-create-input
    (check-email-unique components)
    enrich-user
    (persist-user components))
```

### Interceptors as Components

Interceptors are defined in `interceptors.clj` and composed into chains in `routes.clj`:

```clojure
(defmethod ig/init-key :service/handlers [_ {:keys [components]}]
  {:user/create [error-handler inject-components parse-body json-response user-create-handler]})
```

Routes are declared in the system EDN:

```edn
{:http/routes {:routes [["/v1/users" :post :user/create]]}}
```

### Event Sourcing

Events are stored in PostgreSQL and published to Kafka:

```
validate → enrich → persist → append-event → publish-kafka
```

## Makefile Commands

| Command                     | Description                              |
|-----------------------------|------------------------------------------|
| `make run`                  | Run with default (stag) profile          |
| `make stag`                 | Run with staging profile                 |
| `make prod`                 | Run with production profile              |
| `make test`                 | Run tests (test profile)                 |
| `make test-stag`            | Run tests with staging config            |
| `make coverage`             | Run tests + coverage report              |
| `make build`                | Build uberjar                            |
| `make docker-up`            | Start infra (Postgres, Redis, Kafka)     |
| `make docker-down`          | Stop infra                               |
| `make init NAME=my-service` | Scaffold new microservice                |

## Demo Endpoints

```bash
# Health
curl http://localhost:8080/health

# Create user
curl -X POST http://localhost:8080/v1/users \
  -H "Content-Type: application/json" \
  -d '{"name":"André Borba","email":"andre@borba.com"}'

# Get user
curl http://localhost:8080/v1/users/<uuid>

# Create order
curl -X POST http://localhost:8080/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"user-id":"<uuid>","items":[{"product":"Widget","quantity":2,"price":29.99}]}'

# Get order
curl http://localhost:8080/v1/orders/<uuid>
```

## License

MIT © Borba
