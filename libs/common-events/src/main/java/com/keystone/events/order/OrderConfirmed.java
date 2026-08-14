package com.keystone.events.order;

import com.keystone.events.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record OrderConfirmed(UUID eventId, UUID sagaId, Instant occurredAt, UUID orderId) implements DomainEvent {

    public static OrderConfirmed of(UUID orderId) {
        return new OrderConfirmed(UUID.randomUUID(), orderId, Instant.now(), orderId);
    }
}
