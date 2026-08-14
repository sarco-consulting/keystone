# 0004. Multiple Message Types Per Topic, Dispatched by a Header

Date: 2026-08-14

## Status

Accepted

## Context

`inventory.commands` carries both `ReserveInventoryCommand` and
`ReleaseInventoryCommand`. `inventory.events` carries `InventoryReserved`,
`InventoryReservationFailed`, and `InventoryReleased`. `order-service`'s
single `OrderResultListener` consumes both `inventory.events` and
`payment.events`, five event types total, in one `@KafkaListener` method.

The alternative — one topic per message type — was considered and rejected.
It would mean nine topics instead of five for the same five services'
message vocabulary, and more importantly it would scatter what is a single
saga's outcome-handling logic across nine separate `@KafkaListener` methods
instead of one per consumer that can see the whole picture in one place.
Partition ordering also favors this: Kafka only guarantees order within a
partition for a given key, and every message here is keyed by `orderId` —
splitting types across topics wouldn't preserve any additional ordering
guarantee that keying alone doesn't already provide.

## Decision

Each consumer listens to whichever topic(s) it needs, and every message
carries a `event-type` header (see `MessagingHeaders.EVENT_TYPE`) set by the
outbox relay publisher when it sends. The consumer reads that header,
switches on it to pick the correct record type to deserialize into, then
dispatches with a Java 21 pattern-matching `switch` over the resulting
`DomainEvent`.

The header lives outside the JSON payload deliberately: the payload stays a
direct, unmodified serialization of the event/command record, with no
wrapper envelope or discriminator field polluting the domain shape.

## Consequences

- Five topics instead of nine, and each saga participant's Kafka listener
  reads like a small state machine — one method, one `switch`, the whole
  set of things that can happen at that point in the saga visible together.
- Adding a new message type to an existing topic means updating that
  consumer's `deserialize` switch and `dispatch` switch — two small,
  centralized edits, not a new topic plus new listener plus new
  provisioning.
- The header is load-bearing: if a producer ever sent a message without it
  (bypassing `AbstractOutboxRelayPublisher`), the consumer would log a
  warning and silently ignore the message rather than crash. That's a
  deliberate fail-open choice for unrecognized messages, not a gap — see
  each listener's `default ->` branch.
