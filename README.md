# be-boilerplate

[![CI](https://github.com/AF2B/be-boilerplate/actions/workflows/ci.yml/badge.svg)](https://github.com/AF2B/be-boilerplate/actions/workflows/ci.yml)

> Clojure microservice boilerplate on the Borba framework — Data-Driven, Event-Sourced, Railway-Oriented.

## Stack

| Layer          | Tech                                                                 |
|----------------|----------------------------------------------------------------------|
| Language       | Clojure 1.12 on JDK 21                                               |
| Lifecycle      | `borba-core-component`: Integrant + Aero profiles, readiness, drain  |
| HTTP           | Pedestal 0.8 + Jetty 12 (`borba-server`, `-routes`, `-handlers`, `-interceptors`) |
| Validation     | `clojure.spec.alpha`                                                 |
| Database       | PostgreSQL 16 via `borba-sql-client-component` (next.jdbc + HikariCP) |
| Event Sourcing | `borba-event-store-component` (append-only PostgreSQL table)         |
| Cache          | Redis via `borba-redis-component` (Carmine)                          |
| Messaging      | Kafka 4 via `borba-kafka-producer-component` / `-consumer-component` |
| Contracts      | Confluent Schema Registry: a JSON Schema per topic, checked on send and read |
| Errors         | `borba-railway-component`: Either + HTTP mapping                     |
| Testing        | `clojure.test`, `test.check`, `borba-state-flow-component`, Kaocha   |

## Quick Start

```bash
cp .env.example .env     # local values, never committed
make docker-up           # PostgreSQL, Redis, Kafka, Schema Registry
make run                 # profile stag
```

```bash
curl localhost:8080/health
curl localhost:8080/readyz

curl -X POST localhost:8080/v1/users \
  -H "Content-Type: application/json" \
  -d '{"name":"André Borba","email":"andre@borba.com"}'

curl -X POST localhost:8080/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"user-id":"<uuid>","items":[{"product":"Widget","quantity":2,"price":29.99}]}'
```

## A new service

```bash
make init NAME=payment-service
cd ../payment-service
cp .env.example .env
make docker-up && make run
```

`make init` copies the boilerplate, renames `be-boilerplate` / `be_boilerplate` everywhere
(namespaces, directories, configuration, workflow) and makes the first commit on `main`. The copy is
a service of its own from then on: later changes to the boilerplate do not reach it.

## Project Structure

```
.
├── deps.edn                     # Borba components as git deps, aliases
├── build.clj                    # uberjar (tools.build)
├── Makefile                     # every command, locally and in CI
├── docker-compose.yml           # local PostgreSQL, Redis, Kafka (KRaft), Schema Registry
├── Dockerfile                   # multi-stage image, no default PROFILE
├── .env.example                 # local variables; copy to .env
├── db/migrations/               # SQL applied in order (users, orders, relay positions)
├── dev/user.clj                 # REPL: (go), (halt)
├── .github/workflows/ci.yml     # lint, tests, integration, image
├── src/
│   ├── config.edn               # profile router → system/<profile>.edn
│   └── com/borba/be_boilerplate/
│       ├── system/
│       │   ├── base.edn         # every Integrant key
│       │   ├── stag.edn / prod.edn / test.edn   # per-profile overrides
│       │   ├── components.clj   # :service/components, what handlers can reach
│       │   ├── event_relay.clj  # publishes the event store to Kafka (the outbox)
│       │   └── projection.clj   # event-store folds (empty)
│       ├── handlers/
│       │   ├── http/
│       │   │   ├── handlers.clj      # defmethod registry/handler
│       │   │   └── interceptors.clj  # defmethod registry/interceptor
│       │   └── business/
│       │       ├── user.clj          # Railway
│       │       └── order.clj         # Railway + Event Sourcing + Kafka
│       ├── adapters/             # clients of external services (empty)
│       ├── components/           # Integrant components of the service (empty)
│       ├── repository/           # SQL via borba.sql-client
│       ├── specs/                # clojure.spec contracts
│       ├── events/
│       │   ├── schemas/          # the JSON Schema of each topic (EDN)
│       │   ├── producer.clj      # domain events to Kafka
│       │   └── consumer.clj      # Kafka handlers
│       └── util/predicates.clj
└── test/
    ├── com/borba/be_boilerplate/ # unit tests and properties
    ├── flows/                    # ^:integration state-flow flows
    └── generators/               # test.check generators
```

## Architecture

### Component injection

Every handler and interceptor receives `:service/components`, a map declared in `base.edn`:

```clojure
(defn find-by-id [components id]
  (sql/find-one-by! (:sql-client components) :users {:id id}))
```

### Railway Oriented Programming

Business functions return `Right` or `Left`; a failure is data with an `:error` code that maps to a
status:

```clojure
(rop/|> input
        validate-create-input
        (check-email-unique components)
        enrich-user
        (persist-user components))
```

### Event Sourcing, with the event store as the outbox

An order and its `order-created` event are stored in one transaction. The request never calls Kafka, so it
is neither slowed nor failed by it; the event relay publishes what the event store holds:

```
request:  validate → total → enrich → persist order + append event (one transaction) → 201
relay:    event store feed (after its position) → Kafka, in order → move its position
```

Delivery is at-least-once: the relay commits its position after the events are on Kafka, so consumers must be
safe to receive an event twice. Of the instances of a service, one relays at a time (its row in
`event_relay_positions` is the lock).

### Event contracts

Each topic has a JSON Schema in `events/schemas/`, held by the Schema Registry. The producer validates
every value against it before sending, and the consumer reads every value with it. Locally (`stag`)
the service registers its schemas at start; in `prod` it only checks they are registered, and fails
the start otherwise, so a contract changes on purpose and never as a side effect of a deploy.

### Readiness and graceful shutdown

`/readyz` is `200` only when the lifecycle is ready and the database answers. On `SIGTERM` the service
turns readiness off, keeps serving for `:drain-delay-ms` (15 s in `prod`) so the load balancer stops
routing, then stops the server and the components.

## Makefile Commands

| Command                     | Description                                       |
|-----------------------------|---------------------------------------------------|
| `make run`                  | Run with `PROFILE` (default `stag`)               |
| `make run PROFILE=prod`     | Run with another profile                          |
| `make test`                 | Unit tests                                        |
| `make test-integration`     | Integration flows (needs `make docker-up`)        |
| `make coverage`             | Unit tests + coverage report                      |
| `make lint` / `make fmt-check` | clj-kondo (no warnings) / cljfmt               |
| `make build`                | Uberjar at `target/app.jar`                       |
| `make docker-build`         | Image tagged with sha, version and `latest`       |
| `make docker-up` / `docker-down` | Start / stop the local infrastructure (with the Schema Registry) |
| `make init NAME=x`          | Scaffold a new service in `../x`                  |
| `make outdated`             | Dependencies with a newer version                 |

See [HOW-TO-USE.md](HOW-TO-USE.md) for adding handlers, interceptors, consumers, queries and components.

## License

MIT © Borba
