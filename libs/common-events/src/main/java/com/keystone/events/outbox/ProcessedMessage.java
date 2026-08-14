package com.keystone.events.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.UUID;

/**
 * Consumer-side counterpart to {@link OutboxMessage}: one row per
 * successfully-handled {@code eventId}, inserted in the same transaction as
 * the side effect it guards. Kafka delivery is at-least-once, so a consumer
 * checking this table before acting — and relying on the primary key to
 * reject a concurrent duplicate — is what makes message handling idempotent
 * rather than merely "usually fine."
 */
@MappedSuperclass
public abstract class ProcessedMessage {

    @Id
    protected UUID eventId;

    @Column(name = "processed_at", nullable = false)
    protected Instant processedAt;

    protected ProcessedMessage() {
        // JPA
    }

    protected ProcessedMessage(UUID eventId) {
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }

    public UUID getEventId() {
        return eventId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
