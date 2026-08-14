# Runbook

Operational guide: running the stack, watching a saga happen, and diagnosing
the failure modes that are actually reachable in this system.

## Prerequisites

- JDK 21 (the Gradle toolchain will provision it automatically if missing)
- A container runtime with a `docker compose`-compatible CLI — Docker,
  Podman + `podman-compose`, or Colima. This repo was built and verified
  against Podman. If you hit a Docker Hub anonymous rate-limit error
  pulling images, either `docker login`/`podman login` first, or note that
  `infra/docker-compose.yml` already pulls Redpanda from `docker.io/...`
  rather than `docker.redpanda.com/...` specifically to avoid this — the
  same rate limit applies to any other image you pull anonymously and often.

## Starting everything

```bash
cd infra
docker compose up -d      # or: podman-compose up -d

cd ..
./gradlew :services:order-service:bootRun &
./gradlew :services:inventory-service:bootRun &
./gradlew :services:payment-service:bootRun &
```

Wait for all three to report healthy:

```bash
curl -s http://localhost:8081/actuator/health   # order-service
curl -s http://localhost:8082/actuator/health   # inventory-service
curl -s http://localhost:8083/actuator/health   # payment-service
```

## Watching a saga happen

```bash
# Happy path
curl -s -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"demo","currency":"USD","items":[{"productId":"sku-widget","quantity":2,"unitPrice":15.00}]}'

# Grab the returned "id", then:
curl -s http://localhost:8081/orders/{id}            # current status
curl -s http://localhost:8081/orders/{id}/timeline    # every saga step, in order
```

To see the compensation path, order `sku-widget` with `unitPrice` exactly
`666.66` — WireMock's stub (`infra/wiremock/mappings/authorize-declined.json`)
declines any charge at that amount. To see the insufficient-stock path
instead, order more of `sku-limited-edition` than the seeded catalog holds
(2 units).

## Observability

| What | Where |
|---|---|
| Distributed traces | http://localhost:16686 (Jaeger) — search by service, or paste a trace ID |
| Metrics / dashboards | http://localhost:3000 (Grafana, anonymous viewer access) |
| Raw metrics | http://localhost:9090 (Prometheus) or `/actuator/prometheus` on each service |
| Kafka topics | http://localhost:8090 (Redpanda Console) |
| Structured logs | run any service with `--spring.profiles.active=json-logs`; every saga-related log line carries `sagaId` (== order id) for correlation |

To find every log line for one order across all three services once running
with `json-logs`:

```bash
grep '"sagaId":"<order-id>"' /path/to/each/service.log
```

## Diagnosing failures

**A service won't start / `/actuator/health` never turns UP**
Check the service's own log first — Flyway migration failures and
datasource connectivity issues show up immediately at startup, before
anything else initializes.

**An order is stuck mid-saga (not CONFIRMED or CANCELLED after a few
seconds)**
`GET /orders/{id}/timeline` shows the last step that completed. Cross-
reference with the log line just after it — if the *next* service's outbox
relay is failing to publish, its log will show `Failed to relay outbox
message ... will retry` (see `AbstractOutboxRelayPublisher`).

**Messages landing in `<topic>.DLT`**
A consumer exhausted its retry budget (see `KafkaErrorHandlers` —
exponential backoff, ~5s total). Inspect via Redpanda Console or:

```bash
docker exec <redpanda-container> rpk topic consume payment.commands.DLT --offset start --num 1
```

The most likely cause during local development is the downstream
dependency being unreachable — e.g. `mock-payment-gateway` stopped. Restart
the dependency; new messages will process normally, but anything already in
the DLT needs manual replay (not automated — this is exactly the kind of
gap called out in the Production Hardening Roadmap).

**Circuit breaker open on the payment gateway**
`payment-service` logs will show Resilience4j transitioning the
`payment-gateway` breaker to `OPEN`. It half-opens after the configured wait
duration (10s) and tests recovery automatically — no manual intervention
needed, but new `AuthorizePaymentCommand`s will fail fast (and fall through
to Kafka-level retry/DLT) until it does.

## Running the tests

```bash
./gradlew build              # unit + Testcontainers integration tests, all four modules
./gradlew :e2e-tests:test    # full saga, both the happy path and compensation path
```

The e2e test builds all three service jars, then launches them as real OS
processes against Testcontainers-managed Postgres/Redpanda/WireMock — it
does not reuse whatever's running from "Starting everything" above, and
will bind its own ports (18081–18083) to avoid colliding with it.

## Tearing down

```bash
cd infra && docker compose down -v   # -v also drops the seeded/accumulated data
```
