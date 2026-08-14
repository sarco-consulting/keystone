# Architecture

> See [docs/adr/](adr/) for the reasoning behind individual decisions —
> several of them, especially ADR-0003, were discovered mid-build rather
> than decided upfront.

## System context

```mermaid
flowchart LR
    Client([Client / Postman])
    Client -->|POST /orders| Order[order-service]

    subgraph Keystone
        Order -->|commands| Inventory[inventory-service]
        Order -->|commands| Payment[payment-service]
        Inventory -->|events| Order
        Payment -->|events| Order
    end

    Payment -->|mocked| Gateway[(External payment gateway\nWireMock stub)]
```

## Order saga — happy path

```mermaid
sequenceDiagram
    participant C as Client
    participant O as order-service
    participant I as inventory-service
    participant P as payment-service

    C->>O: POST /orders
    O->>O: persist Order (PENDING) + outbox OrderCreated
    O-->>I: ReserveInventory
    I-->>O: InventoryReserved
    O-->>P: AuthorizePayment
    P-->>O: PaymentAuthorized
    O->>O: mark Order CONFIRMED
    C->>O: GET /orders/{id}/timeline
    O-->>C: full saga step history
```

## Order saga — compensation path

```mermaid
sequenceDiagram
    participant O as order-service
    participant I as inventory-service
    participant P as payment-service

    O-->>I: ReserveInventory
    I-->>O: InventoryReserved
    O-->>P: AuthorizePayment
    P-->>O: PaymentFailed
    O-->>I: ReleaseInventory (compensation)
    I-->>O: InventoryReleased
    O->>O: mark Order CANCELLED
```

## Service responsibilities

| Service | Owns | Does not know about |
|---|---|---|
| `order-service` | Order aggregate, the saga itself (`OrderSagaManager`), outbox | Inventory/Payment internals |
| `inventory-service` | Stock levels, reservation ledger | The saga, Order, Payment |
| `payment-service` | Authorization/void against the (mocked) gateway | The saga, Order, Inventory |

See [ADR-0001](adr/0001-saga-orchestration-over-choreography.md) for why the
saga is orchestrated from `order-service` rather than choreographed.

## Reliability mechanisms

- **Transactional outbox** — every state-changing write and its outgoing
  event commit in the same local transaction, relayed to Kafka/Redpanda by a
  polling publisher. Avoids the dual-write problem. [ADR-0002](adr/0002-transactional-outbox.md)
- **Idempotent consumers** — every consumer records processed event IDs and
  skips duplicates, since Kafka delivery is at-least-once.
- **Correlation, two distinct mechanisms**:
  - Every event/command carries a `sagaId` field (the order id) in its
    payload. Each Kafka listener puts it into the logging MDC for the
    duration of processing (`SagaMdc`), so structured logs across all three
    services can be filtered to one order.
  - Separately, OpenTelemetry trace context (`traceparent`) is propagated
    through the outbox row itself and restored before each Kafka publish —
    see [ADR-0003](adr/0003-trace-context-across-the-outbox-boundary.md) for
    why that needed to be explicit rather than automatic.
- **Multiple message types per Kafka topic**, dispatched by an `event-type`
  header rather than one topic per type — [ADR-0004](adr/0004-multiple-message-types-per-topic.md)
- **Resilience4j** circuit breaker + retry around the one real external
  call (the payment gateway), and Kafka consumer retry-with-backoff falling
  through to a dead-letter topic on all three services.

## Observability

- **Traces**: OpenTelemetry Spring Boot starter, auto-instrumenting Spring
  MVC/JDBC/Kafka, exported via OTLP to Jaeger. A single trace spans an
  entire order — HTTP request through every Kafka hop and DB query across
  all three services — per ADR-0003.
- **Metrics**: Micrometer + Prometheus, scraped from each service's
  `/actuator/prometheus`, visualized in a provisioned Grafana dashboard
  covering both infra metrics (HTTP latency, JVM heap) and saga-specific
  business counters (`keystone.orders.confirmed`, `keystone.orders.cancelled`).
- **Logs**: Spring Boot's built-in structured logging (JSON), profile-gated
  (`json-logs`) so local iteration stays human-readable by default, with
  `sagaId` correlation as described above.

## Testing

Unit tests per service, Testcontainers-backed integration tests per service
against real Postgres, and one Testcontainers-orchestrated end-to-end test
(`e2e-tests`) that launches all three service jars as real processes and
drives the actual saga over HTTP — both the happy path and the compensation
path. See the root [README](../README.md#testing) for how to run each layer.
