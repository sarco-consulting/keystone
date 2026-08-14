package com.keystone.events.inventory;

import com.keystone.events.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record InventoryReserved(UUID eventId, UUID sagaId, Instant occurredAt, UUID orderId) implements DomainEvent {

    public static InventoryReserved of(UUID orderId) {
        return new InventoryReserved(UUID.randomUUID(), orderId, Instant.now(), orderId);
    }
}
