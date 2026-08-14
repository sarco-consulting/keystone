package com.keystone.events.inventory;

import com.keystone.events.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** The compensating command — releases every item reserved for this order. */
public record ReleaseInventoryCommand(UUID eventId, UUID sagaId, Instant occurredAt, UUID orderId) implements DomainEvent {

    public static ReleaseInventoryCommand of(UUID orderId) {
        return new ReleaseInventoryCommand(UUID.randomUUID(), orderId, Instant.now(), orderId);
    }
}
