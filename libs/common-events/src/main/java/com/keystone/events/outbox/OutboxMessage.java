package com.keystone.events.outbox;

import com.keystone.events.DomainEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.UUID;

/**
 * Shared shape for the transactional outbox table each service owns. Reused
 * via {@code @MappedSuperclass} rather than copy-pasted, since the fields are
 * identical across services even though each has its own table/schema —
 * this is the textbook case for it.
 *
 * A row is written in the same local transaction as the business change it
 * announces, and relayed to Kafka afterward by a polling publisher — this is
 * what makes "commit the change" and "publish the event" atomic without a
 * distributed transaction. See ADR-0002.
 */
@MappedSuperclass
public abstract class OutboxMessage {

    @Id
    protected UUID id;

    @Column(name = "saga_id", nullable = false)
    protected UUID sagaId;

    @Column(nullable = false)
    protected String topic;

    @Column(name = "message_key", nullable = false)
    protected String messageKey;

    @Column(name = "message_type", nullable = false)
    protected String messageType;

    @Column(nullable = false)
    protected String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    protected Instant createdAt;

    @Column(name = "published_at")
    protected Instant publishedAt;

    // Nullable: only populated when a trace was actually active at write
    // time (e.g. present in tests that construct events directly). See
    // TraceContextPropagation for why this column exists at all.
    @Column
    protected String traceparent;

    protected OutboxMessage() {
        // JPA
    }

    protected OutboxMessage(DomainEvent event, String topic, String messageKey, String payload, String traceparent) {
        this.id = event.eventId();
        this.sagaId = event.sagaId();
        this.topic = topic;
        this.messageKey = messageKey;
        this.messageType = event.getClass().getSimpleName();
        this.payload = payload;
        this.traceparent = traceparent;
        this.createdAt = Instant.now();
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getSagaId() {
        return sagaId;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getTraceparent() {
        return traceparent;
    }
}
