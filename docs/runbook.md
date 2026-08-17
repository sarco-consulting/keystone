# Runbook

Operational guide: running the stack, watching a saga happen, and diagnosing
the failure modes that are actually reachable in this system.

## Prerequisites

- JDK 21 (the Gradle toolchain will provision it automatically if missing)
- `curl` and `jq` — the examples below (and in the README) pipe curl's
  output through `jq` to pull out fields like `.access_token` and `.id`
- A container runtime with a `docker compose`-compatible CLI — Docker,
  Podman + `podman-compose`, or Colima. This repo was built and verified
  against Podman. If you hit a Docker Hub anonymous rate-limit error
  pulling images, either `docker login`/`podman login` first, or note that
  `infra/docker-compose.yml` already pulls Redpanda from `docker.io/...`
  rather than `docker.redpanda.com/...` specifically to avoid this — the
  same rate limit applies to any other image you pull anonymously and often.
- If you're on **Podman on macOS or Windows** (where Podman runs inside a
  VM), give that VM at least 4GiB: `podman machine set --memory 4096` (stop
  the machine first, then restart it). The default 2GiB allocation
  OOM-kills Keycloak once it's running alongside the three Postgres
  containers, Redpanda, and WireMock — the failure shows up as a
  `ContainerLaunchException` / wait-strategy timeout, not an obvious
  out-of-memory error, so it's easy to misdiagnose as a networking problem.
  This doesn't apply on Linux, where Podman runs natively with no VM.
- Twelve ports must be free on the host — see "Ports used" below. The
  first `docker compose up -d` also pulls eight distinct images (Postgres,
  WireMock, Redpanda, Redpanda Console, Keycloak, Jaeger, Prometheus,
  Grafana); expect it to take a few minutes on a slow connection.

## Ports used

| Port | What |
|---|---|
| 8081 / 8082 / 8083 | order- / inventory- / payment-service |
| 55432 / 55433 / 55434 | order- / inventory- / payment-db (Postgres) |
| 8095 | mock-payment-gateway (WireMock) |
| 9092 | Redpanda (Kafka API) |
| 8090 | Redpanda Console |
| 8180 | Keycloak |
| 16686, 4317, 4318 | Jaeger (UI, OTLP gRPC, OTLP HTTP) |
| 9090 | Prometheus |
| 3000 | Grafana |

If anything's already listening on one of these — 3000, 9090, and 8080-ish
ports are common collisions with other local dev tools — either stop it or
remap the port in `infra/docker-compose.yml` (and the corresponding
service's `application.yml`, since the default ports are baked in there
too).

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

(No need to wait for Keycloak specifically — each service resolves its
OIDC/JWKS configuration lazily, on the first request that actually needs to
validate a token, not at startup. A service will report healthy even if
Keycloak isn't up yet; it just can't successfully authenticate a request
until Keycloak is reachable.)

To stop the three backgrounded services later: if they're still running in
your current shell session, `kill %1 %2 %3` (or `jobs -l` to check what's
running first). From a different shell, find them by port instead:
`lsof -ti:8081,8082,8083 | xargs kill`.

## Watching a saga happen

Fetch a token first (see "Starting everything" above), then:

```bash
# Happy path
curl -s -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"customerId":"demo","currency":"USD","items":[{"productId":"sku-widget","quantity":2,"unitPrice":15.00}]}'

# Grab the returned "id", then:
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8081/orders/{id}            # current status
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8081/orders/{id}/timeline    # every saga step, in order
```

To see the compensation path, order **1 unit** of `sku-widget` with
`unitPrice` exactly `666.66` — WireMock's stub
(`infra/wiremock/mappings/authorize-declined.json`) declines a charge only
when the *total* amount sent to the gateway is exactly `666.66`
(`bodyPatterns` matches `amount == 666.66`), which is `quantity ×
unitPrice`, not `unitPrice` alone. `{"productId":"sku-widget","quantity":1,"unitPrice":666.66}`
works; `quantity:2` at that price totals `1333.32` and authorizes normally
instead. To see the insufficient-stock path, order more of
`sku-limited-edition` than the seeded catalog holds (2 units).

**Or skip curl and use Swagger UI** — `http://localhost:8081/swagger-ui.html`
(8082/8083 for the other two services). Click **Authorize**: either let it
fetch a token from Keycloak directly (`keycloak-client-credentials`), or
paste one you fetched via curl into the plain `bearer-token` field — use
the latter if the former errors with a CORS/`Failed to fetch` message (see
"Diagnosing failures" below). Once authorized, "Try it out" on any endpoint
attaches the token automatically. See
[ADR-0006](adr/0006-swagger-ui-authorize-and-keycloak-cors.md).

## Observability

| What | Where |
|---|---|
| Distributed traces | http://localhost:16686 (Jaeger) — search by service, or paste a trace ID |
| Metrics / dashboards | http://localhost:3000 (Grafana, anonymous viewer access) |
| Raw metrics | http://localhost:9090 (Prometheus) or `/actuator/prometheus` on each service |
| Kafka topics | http://localhost:8090 (Redpanda Console) |
| Auth realm/clients | http://localhost:8180/admin/ (Keycloak admin console) — login `admin` / `keystone`, then switch the realm selector to `keystone` |
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

**Swagger UI's "Authorize" fails with a CORS error / `TypeError: Failed to
fetch`, even though `infra/keycloak/keystone-realm.json`'s client has
`webOrigins` set correctly**
Keycloak only imports a realm if one by that name doesn't already exist yet
(`--import-realm`'s default `IGNORE_EXISTING` strategy) — check the
container's logs for `Realm 'keystone' already exists. Import skipped`.
If your Keycloak container has been running since before you last edited
the realm file, your edit was silently ignored on every restart since; the
container's *persisted* client config is what's actually being served, not
the file. Fix: recreate the container rather than just restarting it —
`podman-compose up -d --force-recreate keycloak` (or `docker compose`
equivalent) — or `docker compose down -v && docker compose up -d` for a
full reset. The plain-bearer entry in Swagger's Authorize dialog (paste a
token fetched via curl) works regardless of this and needs no realm import.
See [ADR-0006](adr/0006-swagger-ui-authorize-and-keycloak-cors.md) for the
full story.

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
lsof -ti:8081,8082,8083 | xargs kill   # the three backgrounded services — see "Starting everything"
cd infra && docker compose down -v     # -v also drops the seeded/accumulated data
```
