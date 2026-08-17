# 0007. Load Testing the Order Saga With Gatling

Date: 2026-08-17

## Status

Accepted

## Context

The resilience mechanisms this repo demonstrates — circuit breaker, retry, DLT, saga compensation — were, until now, only verified manually (README's Resilience section: "Verified by actually stopping the payment gateway mid-saga"). That's a real verification, but a one-off, undocumented one. Turning "these mechanisms exist" into "these mechanisms hold under concurrent load, with real numbers" is a materially stronger claim, and the more valuable one to have proof of.

## Decision

A new Gradle module, `load-tests/`, uses the **Gatling Gradle plugin** to drive the real order saga under concurrent load — chosen specifically over k6/Locust/JMeter to keep the whole repo on a single Java/Gradle toolchain, matching every other module. It targets the **already-running local stack** (`docs/runbook.md`'s "Starting everything"), not an ephemeral Testcontainers instance, so a run is observable live on the Grafana dashboard already provisioned in `infra/grafana`.

**Scenario** (`OrderSagaLoadSimulation.java`, mirrors `OrderSagaEndToEndTest`'s happy path under concurrency): create an order for `sku-widget` at a normal price, then poll `GET /orders/{id}` until `CONFIRMED`, then spot-check `GET /orders/{id}/timeline` on a fraction of confirmed orders. A single `client_credentials` token is fetched once before injection starts (one trusted caller, not one token per user) and reused for the whole run — no refresh logic, since the run comfortably finishes inside Keycloak's 300s token expiry. This is a stated scope boundary, not an oversight.

**Load profile**: an open workload (arrival rate independent of response time — more realistic under an async saga than a closed/fixed-VU-count model) ramping 1→5 orders/sec over 10s, holding 5/sec for 40s, ramping down over 10s. Sized to stay comfortably under `sku-widget`'s 500-unit seeded seed stock for a *single* run.

**Not wired into the default CI build** — same rationale as `e2e-tests` being a separate task, doubled: shared/noisy CI runners make latency numbers meaningless, and the entire point is watching a real, already-running local stack, not a fresh ephemeral one.

### Two things discovered mid-build, not decided upfront

**Group duration vs. cumulated response time.** The scenario wraps create+poll+verify in a Gatling `group("Saga Completion")`, to get true end-to-end saga latency as a first-class reported stat. Gatling's default for a group's reported "response time" is *cumulated response time* — the sum of its child requests' individual response times, which silently excludes the `pause()` calls between polls. That understates real end-to-end latency. `gatling.charting.useGroupDurationMetric = true` in `load-tests/src/gatling/resources/gatling.conf` switches this to the group's actual wall-clock duration. Gatling's own assertions documentation states group-scoped `.assertions()` always use cumulated response time regardless of this flag — so the group duration is reported and read from the HTML output (see `docs/load-test.md`), but the hard, build-failing `.assertions()` are deliberately scoped to the well-documented, unambiguous global HTTP response-time and success-rate metrics instead, not the group's duration.

**A same-database gotcha that produced a real, honest, and initially confusing result.** Because the load test runs against the same already-running, persistent Postgres (by design — see above), running it repeatedly without resetting the stack accumulates `sku-widget` reservations across runs. After a few full runs the seeded 500-unit stock hit zero, and every subsequent order legitimately went through the *insufficient-stock compensation path* instead of confirming — the system behaved correctly throughout, but it meant a "fraction of confirmed orders" correctness check (`doIf` gating the timeline spot-check) was silently never triggering, because no orders were reaching `CONFIRMED` anymore. Diagnosed with a temporary debug hook rather than guessed at. Fixed for this run by resetting the stack (`docker compose down -v && up -d`) before measuring; documented as an operational note in `docs/runbook.md` so a future re-run isn't misread as a system bug when it's actually expected behavior under real, cumulative resource exhaustion.

## Consequences

**Easier**: the resilience/correctness claims now have a real, repeatable, documented artifact behind them — see `docs/load-test.md` for the actual numbers from a real run, not estimates. The scenario doubles as a correctness check under concurrency (the timeline spot-check), not just a throughput one.

**Harder**: re-running the load test against a long-lived local stack requires resetting seeded data first, or accepting that later runs will exercise the real insufficient-stock path instead of the happy path — this is now documented, not a trap. The `client_credentials`-once-per-run design means a load test longer than ~5 minutes would need token-refresh logic that doesn't exist yet.

**Explicitly out of scope**: a true capacity/soak test (this is demo-scale — comfortably under seeded stock, ~60 seconds, not a search for the system's actual breaking point), chaos/fault-injection under load (a natural follow-up, not this pass), and CI integration (deliberately manual/local, per the "watchable on Grafana" rationale above).
