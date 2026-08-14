package com.keystone.events.payment;

import com.keystone.events.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AuthorizePaymentCommand(
        UUID eventId, UUID sagaId, Instant occurredAt, UUID orderId, BigDecimal amount, String currency)
        implements DomainEvent {

    public static AuthorizePaymentCommand of(UUID orderId, BigDecimal amount, String currency) {
        return new AuthorizePaymentCommand(UUID.randomUUID(), orderId, Instant.now(), orderId, amount, currency);
    }
}
