# 0003. Propagate Trace Context Explicitly Across the Outbox Boundary

Date: 2026-08-14

## Status

Accepted

## Context

With OpenTelemetry auto-instrumentation wired in (Spring MVC, JDBC, Kafka —
see the observability section of docs/architecture.md), the expectation was
that placing an order would produce a single trace spanning all three
services: the HTTP request, the Kafka commands and events, all of it.

It didn't. Every hop showed up as its own brand-new trace, each with exactly
one service in it. The `GET /orders/{id}/timeline` promise of "watch the
saga happen" was fine; the equivalent promise for tracing wasn't holding.

The root cause is the transactional outbox itself (ADR-0002). Auto-
instrumentation propagates whatever trace context is *current* at the
moment `KafkaTemplate.send()` runs. But that send doesn't happen at request
time — it happens later, when `AbstractOutboxRelayPublisher`'s `@Scheduled`
poller picks up the row and sends it. By then the original HTTP request has
long since returned; the scheduler thread has no ambient relationship to it
at all. Auto-instrumentation faithfully injects the *scheduler's own*
(brand new) trace context into the outgoing message — which is exactly
correct behavior for auto-instrumentation, and exactly wrong for what we
wanted, because the outbox pattern deliberately decouples "decide to
publish" from "actually publish" in time.

This is a general property of the outbox pattern, not a bug in a specific
library: any async relay that defers publishing will break naive trace
propagation the same way.

## Decision

Carry the trace context through the outbox row itself.
`AbstractOutboxWriter.write()` captures the current trace as a W3C
`traceparent` string (via an injected `OpenTelemetry` instance — not
`GlobalOpenTelemetry.get()`, which returns a no-op instance here because the
Spring Boot starter exposes the SDK as a bean rather than registering it
globally) and stores it on the outbox row. `AbstractOutboxRelayPublisher`
reads it back and makes it the current context for the duration of the
`send()` call. Auto-instrumentation does the rest — it injects whatever
context is current, which is now the *original* one, not the scheduler's.

No changes were needed on the consumer side: `@KafkaListener` auto-
instrumentation already extracts an incoming `traceparent` header into a
proper parent span. The break was entirely on the producer side, and only
because of the outbox's async gap.

## Consequences

- A single Jaeger trace now spans the entire saga — HTTP request, every
  outbox write, every Kafka publish and consume, every DB query along the
  way — across all three services, for a single order.
- The `outbox_messages` table carries one more nullable column
  (`traceparent`) per service. Nullable because not every write happens
  inside an active trace (unit tests constructing events directly, for
  instance) — a missing value just means the relay proceeds without
  restoring a specific context, not an error.
- This pattern generalizes: anything that defers work across a thread or
  process boundary — a scheduler, a queue, a thread pool — needs the same
  explicit capture-and-restore treatment if trace continuity across that
  boundary matters. It is easy to get instrumentation coverage everywhere
  and still end up with fragmented traces because of exactly this gap.
