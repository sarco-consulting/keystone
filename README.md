# Keystone

**A reference implementation of a distributed order-fulfillment saga** — the
kind of problem that shows up in every real e-commerce or logistics platform:
committing an order requires reserving inventory *and* authorizing payment as
one logical transaction, across three services that each own their own
database, with correct rollback when any step fails.

## Why this exists

This is a portfolio project, built to demonstrate senior backend/distributed-
systems engineering end-to-end — not just a working demo, but the full
software delivery lifecycle: architecture decisions recorded as they were
made (not reconstructed after the fact), a real testing pyramid, CI/CD, full
observability, and a clear-eyed account of what's deliberately out of scope.

It is intentionally **not** a finished product — see
[Production Hardening Roadmap](#production-hardening-roadmap) below. What it
demonstrates on purpose is the hard part: a correct distributed transaction
with compensation, made observable and resilient — not the integration labor
of a real payment/IAM/deployment stack around it.

## Architecture

Three Spring Boot services, one saga orchestrator, one shared event backbone.
Full detail, including sequence diagrams for both the happy path and the
compensation path: [docs/architecture.md](docs/architecture.md). Every
non-obvious design decision is recorded in [docs/adr/](docs/adr/) as it was
made, including two I didn't expect going in — see ADR-0003 in particular.

```
order-service  ──commands──►  inventory-service
      │        ◄──events───          │
      │                              │
      └──commands──► payment-service ┘
              ◄──events───
```

- **Saga orchestration**, not choreography — the whole order lifecycle is
  readable in one place. [ADR-0001](docs/adr/0001-saga-orchestration-over-choreography.md)
- **Transactional outbox** for reliable event publishing, shared across all
  three services via `@MappedSuperclass`. [ADR-0002](docs/adr/0002-transactional-outbox.md)
- **Idempotent consumers** via a dedupe table — required because the outbox
  gives at-least-once delivery, not exactly-once
- **A single distributed trace per order**, across all three services —
  which required explicitly propagating trace context through the outbox,
  since auto-instrumentation alone doesn't survive an async relay boundary.
  [ADR-0003](docs/adr/0003-trace-context-across-the-outbox-boundary.md)
- **`GET /orders/{id}/timeline`** — watch a distributed transaction unfold in
  one API call, backed by a persisted saga-step audit trail

## Running it locally

```bash
cd infra && docker compose up -d      # Postgres x3, Redpanda, WireMock, Jaeger, Prometheus, Grafana
cd ..
./gradlew :services:order-service:bootRun &        # :8081
./gradlew :services:inventory-service:bootRun &     # :8082
./gradlew :services:payment-service:bootRun &       # :8083
```

Then place an order and watch it happen:

```bash
curl -s -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"demo","currency":"USD","items":[{"productId":"sku-widget","quantity":2,"unitPrice":15.00}]}'

curl -s http://localhost:8081/orders/{id}/timeline    # the id from the response above
```

Full walkthrough — including how to trigger the compensation and
insufficient-stock paths, and where to find traces/metrics/logs —
in [docs/runbook.md](docs/runbook.md).

## Observability

| | |
|---|---|
| Distributed traces | Jaeger — `localhost:16686` |
| Dashboards | Grafana — `localhost:3000` (anonymous viewer access) |
| Raw metrics | Prometheus — `localhost:9090`, or `/actuator/prometheus` per service |
| Kafka topics | Redpanda Console — `localhost:8090` |
| Structured logs | `--spring.profiles.active=json-logs`; every saga log line carries `sagaId` for cross-service correlation |

## Resilience

- Circuit breaker + retry (Resilience4j) around the one real external
  dependency — the payment gateway call
- Kafka consumer retry with exponential backoff, falling through to a
  dead-letter topic (`<topic>.DLT`) for messages that exhaust it, on all
  three services
- Verified by actually stopping the payment gateway mid-saga and confirming
  the system degrades and recovers correctly — see docs/runbook.md

## Tech stack

Java 21 · Spring Boot 3.5 · Kafka-API (Redpanda) · PostgreSQL · Resilience4j
· OpenTelemetry · Micrometer/Prometheus/Grafana · Testcontainers · Gradle
(Kotlin DSL, multi-module)

## Repository layout

```
services/          order-service, inventory-service, payment-service
libs/common-events/ shared event/command contracts, outbox + tracing utils
e2e-tests/          full-saga end-to-end test (Testcontainers-orchestrated)
infra/              docker-compose stack, WireMock stubs, Grafana dashboards
docs/               architecture, ADRs, runbook
```

## Testing

The full pyramid, 43 tests, all passing against real dependencies — no
mocked databases or in-memory substitutes anywhere in it:

- **Unit tests** per service — domain logic, application services, the saga
  state machine
- **Testcontainers integration tests** per service — the real REST API
  against real Postgres, which is what originally caught a
  `LazyInitializationException` that a mocked repository never would have
- **One end-to-end test** ([e2e-tests](e2e-tests)) — the actual three
  service jars, launched as real processes, against Testcontainers-managed
  Postgres/Redpanda/WireMock, driven purely over HTTP, asserting the exact
  saga-step sequence for both the happy path and the compensation path

```bash
./gradlew build              # unit + integration tests, all four modules
./gradlew :e2e-tests:test    # the full saga, both branches
```

## CI/CD

GitHub Actions ([.github/workflows/ci.yml](.github/workflows/ci.yml)): the
full test suite (including the end-to-end saga test) on every push and PR,
a Trivy dependency vulnerability scan uploaded to the Security tab, and —
on merge to `main` only — Docker images built and published to GHCR for all
three services, each also scanned before publish.

## Production Hardening Roadmap

What's explicitly out of scope, by design — this is what makes the repo
non-stealable and doubles as an honest account of what a real deployment
would still need:

- **No authentication or authorization on any API.** Every endpoint is
  wide open. This is the single biggest gap versus anything internet-facing
  and would be the first thing added for real use (OAuth2/SSO via Keycloak,
  or at minimum an API-key check).
- Real payment gateway integration (currently WireMock-stubbed, clearly
  labeled as such)
- Multi-tenancy, billing, admin UI
- Kubernetes/Helm deployment manifests, Terraform-provisioned infrastructure
- High-availability / multi-region deployment
- Outbox table housekeeping (published rows accumulate — see ADR-0002)
- DLT replay tooling (messages that land in a dead-letter topic today need
  manual inspection and replay — see docs/runbook.md)

## License

MIT — see [LICENSE](LICENSE).
