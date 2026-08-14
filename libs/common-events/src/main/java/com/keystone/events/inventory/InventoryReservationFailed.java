package com.keystone.events.inventory;

import com.keystone.events.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record InventoryReservationFailed(
        UUID eventId, UUID sagaId, Instant occurredAt, UUID orderId, String reason) implements DomainEvent {

    public static InventoryReservationFailed of(UUID orderId, String reason) {
        return new InventoryReservationFailed(UUID.randomUUID(), orderId, Instant.now(), orderId, reason);
    }
}
