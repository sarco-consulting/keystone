package com.keystone.events.inventory;

import com.keystone.events.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record InventoryReleased(UUID eventId, UUID sagaId, Instant occurredAt, UUID orderId) implements DomainEvent {

    public static InventoryReleased of(UUID orderId) {
        return new InventoryReleased(UUID.randomUUID(), orderId, Instant.now(), orderId);
    }
}
