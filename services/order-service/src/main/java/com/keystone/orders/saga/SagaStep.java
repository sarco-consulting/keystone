package com.keystone.orders.saga;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One row per saga transition. This is what {@code GET /orders/{id}/timeline}
 * reads — a persisted audit trail of the distributed transaction, not just
 * the order's current status.
 */
@Entity
@Table(name = "saga_steps")
public class SagaStep {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaStepType step;

    @Column
    private String detail;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected SagaStep() {
        // JPA
    }

    public SagaStep(UUID orderId, SagaStepType step, String detail) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.step = step;
        this.detail = detail;
        this.occurredAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public SagaStepType getStep() {
        return step;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
