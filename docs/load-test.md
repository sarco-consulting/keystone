# Load Testing the Order Saga

Proves the resilience claims (saga orchestration, compensation, idempotency)
hold under concurrent load, not just in isolated manual testing — with real,
repeatable numbers. See [ADR-0007](adr/0007-load-testing-with-gatling.md)
for why Gatling, why this scenario, and two things discovered mid-build.

## Scenario

[`OrderSagaLoadSimulation.java`](../load-tests/src/gatling/java/com/keystone/loadtest/OrderSagaLoadSimulation.java)
mirrors `OrderSagaEndToEndTest`'s happy path, under concurrency:

1. `POST /orders` — one unit of `sku-widget` at a normal price.
2. Poll `GET /orders/{id}` until `status == CONFIRMED` (bounded retries,
   300ms apart).
3. On a fraction of *confirmed* orders, `GET /orders/{id}/timeline` and
   assert the exact expected step sequence — a correctness check under
   concurrency, not just a throughput one.

A single `client_credentials` token is fetched once before the run starts
and reused throughout (see ADR-0007 for why that's a deliberate boundary,
not an oversight).

## Load profile

An open workload (arrival rate independent of response time):

```
ramp   1 → 5 orders/sec  over 10s
hold     5 orders/sec    for 40s
ramp   5 → 0 orders/sec  over 10s
```

~60 seconds total, sized to stay comfortably under `sku-widget`'s 500-unit
seeded stock for one run (see "Running it" below for what happens on repeat
runs without a reset).

## Assertions

Build-failing, via Gatling's `.assertions(...)`:

- Global HTTP response time, 95th percentile, under 1000ms
- Global success rate above 99%

Scoped deliberately to well-documented, unambiguous metrics — see ADR-0007
for why the "Saga Completion" group's own duration is reported (below) but
not hard-asserted on.

## Running it

Prerequisite: the stack already running (`docs/runbook.md`'s "Starting
everything").

```bash
./gradlew :load-tests:gatlingRun
```

Opens a self-contained HTML report at
`load-tests/build/reports/gatling/<timestamp>/index.html` when done.

**Re-running note**: this targets the same persistent stack every time (by
design — see ADR-0007), so repeated runs accumulate `sku-widget`
reservations. After enough cumulative orders exhaust the seeded 500 units,
subsequent runs will legitimately exercise the *insufficient-stock
compensation path* instead of the happy path — not a bug, just real
resource exhaustion across runs. Reset with `docker compose down -v && docker
compose up -d` (from `infra/`) between runs for a clean happy-path
measurement.

## Results (real run, 2026-08-17)

255 orders, **100% success, zero failures**, against a freshly-reset stack.

**Global** (all 1,381 requests — Create Order, Poll Order Status, Verify Timeline):

| Metric | Value |
|---|---|
| Success rate | 100% (0 KO) |
| Mean throughput | 22.6 req/s |
| p50 / p95 / p99 response time | 3ms / 11ms / 15ms |
| Max response time | 163ms |

**Saga completion** (the "Saga Completion" group's true wall-clock duration
— HTTP response for `POST /orders` through the order reaching `CONFIRMED`,
including polling pauses; see ADR-0007 for why this differs from Gatling's
group-assertion default):

| Metric | Value |
|---|---|
| Orders confirmed | 255 / 255 (100%) |
| Mean throughput | 4.2 confirmed orders/sec |
| Min | 917ms |
| p50 | 1229ms |
| p75 | 1524ms |
| p95 | 1548ms |
| p99 | 2044ms |
| Max | 2133ms |

**Per-request breakdown**:

| Request | Count | p50 | p95 |
|---|---|---|---|
| Create Order | 255 | 9ms | 15ms |
| Poll Order Status | 1,091 | 2ms | 4ms |
| Verify Timeline | 35 | 3ms | 5ms |

All 35 timeline spot-checks passed — the exact expected step sequence
(`ORDER_CREATED` → `INVENTORY_RESERVE_REQUESTED` → `INVENTORY_RESERVED` →
`PAYMENT_AUTHORIZE_REQUESTED` → `PAYMENT_AUTHORIZED` → `ORDER_CONFIRMED`)
held under concurrent load, not just in isolation.

**Reading these numbers**: individual HTTP calls are fast (single-digit
milliseconds locally) — the ~1.2s median *saga* completion time is the real
cost of the distributed transaction itself: two async Kafka hops each way
(order→inventory, inventory→order, order→payment, payment→order) plus this
scenario's own 300ms poll interval, not server-side processing time. That
gap between "HTTP is fast" and "the distributed transaction takes over a
second" is precisely the kind of thing a load test — as opposed to manual
testing — makes visible.
