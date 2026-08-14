package com.keystone.events.payment;

import com.keystone.events.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record PaymentAuthorized(
        UUID eventId, UUID sagaId, Instant occurredAt, UUID orderId, String gatewayReference) implements DomainEvent {

    public static PaymentAuthorized of(UUID orderId, String gatewayReference) {
        return new PaymentAuthorized(UUID.randomUUID(), orderId, Instant.now(), orderId, gatewayReference);
    }
}
