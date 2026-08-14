# 0002. Transactional Outbox for Reliable Event Publishing

Date: 2026-08-14

## Status

Accepted

## Context

Every saga transition needs to both commit a local database change (an
order's status, a reservation, a payment authorization) *and* publish an
event announcing it, so the next hop in the saga can react. Doing these as
two separate operations — write to Postgres, then call
`kafkaTemplate.send()` — has a well-known failure mode: the process can
crash (or the network can fail) between the two, leaving the database
committed but the event never published. The saga silently stalls with no
error anywhere.

A two-phase commit across Postgres and Kafka isn't a realistic option (Kafka
doesn't participate in XA transactions in any way we'd want to rely on), and
neither is "just retry the Kafka send until it works" — that doesn't help if
the process crashes before the retry loop even starts.

## Decision

Each service owns an `outbox_messages` table. Writing a domain event means
inserting a row into that table in the *same local transaction* as the
business change — an ordinary Postgres insert, fully covered by the ACID
guarantee already in place. A separate `@Scheduled` poller
(`AbstractOutboxRelayPublisher` in common-events, subclassed per service)
picks up unpublished rows and relays them to Kafka, marking each row
published only after the broker acknowledges the send.

The entity shape (`OutboxMessage`) and the relay/writer logic are shared via
`@MappedSuperclass` and abstract base classes in common-events rather than
copy-pasted three times — see those classes' Javadoc for the extraction
reasoning.

## Consequences

- The database commit and the "this happened" announcement can never
  diverge: either both happen (normal case) or neither does (transaction
  rolled back). There's no window where one succeeds and the other is lost.
- Delivery becomes at-least-once, not exactly-once: a crash between sending
  and marking a row published means it gets relayed again next poll. Every
  consumer has to be idempotent as a result — see `ProcessedMessage` and
  ADR-0001's note on `OrderResultListener`.
- There's a small, bounded publish latency (the poll interval, 500ms by
  default) between commit and Kafka delivery, instead of immediate publish.
  For this saga's timescales that's negligible.
- The outbox table grows unless pruned. Not addressed here — a housekeeping
  job to delete old published rows is a natural follow-on, not built for
  this scope.
