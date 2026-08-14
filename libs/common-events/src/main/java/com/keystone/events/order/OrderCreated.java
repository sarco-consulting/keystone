package com.keystone.events.order;

import com.keystone.events.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderCreated(
        UUID eventId,
        UUID sagaId,
        Instant occurredAt,
        UUID orderId,
        String customerId,
        BigDecimal totalAmount,
        String currency) implements DomainEvent {

    public static OrderCreated of(UUID orderId, String customerId, BigDecimal totalAmount, String currency) {
        return new OrderCreated(UUID.randomUUID(), orderId, Instant.now(), orderId, customerId, totalAmount, currency);
    }
}
