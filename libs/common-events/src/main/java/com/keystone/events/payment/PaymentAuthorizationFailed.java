package com.keystone.events.payment;

import com.keystone.events.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record PaymentAuthorizationFailed(
        UUID eventId, UUID sagaId, Instant occurredAt, UUID orderId, String reason) implements DomainEvent {

    public static PaymentAuthorizationFailed of(UUID orderId, String reason) {
        return new PaymentAuthorizationFailed(UUID.randomUUID(), orderId, Instant.now(), orderId, reason);
    }
}
