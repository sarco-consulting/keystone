# 0001. Use Saga Orchestration, Not Choreography

Date: 2026-08-14

## Status

Accepted

## Context

Placing an order requires a logically atomic transaction across three
independently-owned services (Order, Inventory, Payment), which rules out a
database transaction or two-phase commit. The standard alternative is the
Saga pattern, which can be implemented two ways:

- **Choreography**: each service reacts to the previous service's event and
  decides what to do next on its own. No central coordinator.
- **Orchestration**: a single component (the `OrderSagaManager`, living in
  `order-service`) owns the sequence, issues commands to each participant,
  and interprets the results.

Choreography scales better organizationally as the number of participating
services grows (no shared coordinator to become a bottleneck or single point
of ownership), but it makes the *overall* transaction flow implicit —
reconstructing "what happens when a payment fails" means reading event
handlers scattered across every service.

## Decision

Use orchestration. `order-service` owns an explicit `OrderSagaManager` state
machine that issues commands (`ReserveInventory`, `AuthorizePayment`) and
reacts to result events, including firing the compensating command
(`ReleaseInventory`) on failure.

This is a deliberate choice for a 3-participant saga, not a default: at
significantly larger participant counts (project roadmap territory, not this
build), the coordinator would need to be extracted into its own service to
avoid coupling saga logic to the Order domain, and choreography would be
worth revisiting for the highest-fan-out steps.

## Consequences

- The entire order lifecycle is readable in one place
  (`OrderSagaManager` + `GET /orders/{id}/timeline`), which is a significant
  win for both operability and for anyone auditing this system.
- `order-service` has an outsized responsibility compared to Inventory and
  Payment — it knows about the saga, they don't need to. This is intentional:
  Inventory and Payment stay simple command-handlers, which is where the
  complexity should *not* live.
- `order-service` becomes the thing that must stay available for the saga to
  progress. Reserve/release and authorize/void are each individually
  idempotent specifically so a saga step can be safely retried after an
  `order-service` restart without double-charging or double-reserving.
