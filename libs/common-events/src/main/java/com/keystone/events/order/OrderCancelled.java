package com.keystone.events.order;

import com.keystone.events.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record OrderCancelled(UUID eventId, UUID sagaId, Instant occurredAt, UUID orderId, String reason)
        implements DomainEvent {

    public static OrderCancelled of(UUID orderId, String reason) {
        return new OrderCancelled(UUID.randomUUID(), orderId, Instant.now(), orderId, reason);
    }
}
