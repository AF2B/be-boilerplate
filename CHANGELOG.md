# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project
adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed

- **Breaking:** the Borba components come from GitHub (`io.github.af2b/*`) at
  their 2.x releases (core 2.1.0, interceptors 1.1.1), in place of the 1.x
  releases on GitLab.
- **Breaking:** the profile is required, as the first argument or in
  `PROFILE`. `make run` defaults to `stag`; the image has no default.
- **Breaking:** the database variables are `DATABASE_URL`, `DATABASE_USER` and
  `DATABASE_PASSWORD`, with no default in the configuration. Local values live
  in `.env`, from `.env.example`.
- **Breaking:** the components handed to handlers and interceptors are
  `:service/components`, which also carries the lifecycle.
- An order and its `order-created` event are stored in one transaction, and
  the event is published to Kafka after it.
- Money is a `BigDecimal` from the request to the database and back.
- A repeated email is a `409` with `user/email-already-exists`, also when two
  requests race past the check.
- Ids are UUIDs; an id that is not one is a `404`.
- Kafka runs in KRaft mode; ZooKeeper is gone.
- The pipeline is GitHub Actions: lint, formatting, unit tests, integration
  flows against PostgreSQL, and the image, pushed to GHCR on a version tag.
- `make init` creates the service on `main` and leaves out `.env`, caches and
  this changelog.

### Added

- The Schema Registry: the order-events topic has a JSON Schema
  (`events/schemas/order_events.edn`) that every value is checked against
  before it is sent and when it is read. `stag` registers the schemas at
  start; `prod` fails the start when one is not registered.
- `SCHEMA_REGISTRY_URL`, and the registry in `docker-compose.yml`, on a Kafka
  that also listens on the network of the compose.
- `/readyz`, which follows the lifecycle and the database, and a graceful
  drain on `SIGTERM` (15 s in `prod`).
- `build.clj`, which the `:build` alias named and did not exist.
- Integration flows run by `make test-integration`, and unit properties with
  the generators.
- `dev/user.clj` with `(go)` and `(halt)`.

### Removed

- The `events` table from the migration: the event store creates it, with its
  append-only triggers.
- `util/validation.clj`, `ensure-table!` and the unused event publishers.

## [1.0.0]

- First release.
