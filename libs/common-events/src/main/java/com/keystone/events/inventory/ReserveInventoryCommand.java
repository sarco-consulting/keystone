package com.keystone.events.inventory;

import com.keystone.events.DomainEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Carries every line item for the order so inventory-service can reserve
 * them as one atomic unit — matching the single command/event pair in
 * docs/architecture.md rather than fanning out a saga step per line item.
 */
public record ReserveInventoryCommand(
        UUID eventId,
        UUID sagaId,
        Instant occurredAt,
        UUID orderId,
        List<LineItem> items) implements DomainEvent {

    public static ReserveInventoryCommand of(UUID orderId, List<LineItem> items) {
        return new ReserveInventoryCommand(UUID.randomUUID(), orderId, Instant.now(), orderId, items);
    }

    public record LineItem(String productId, int quantity) {
    }
}
