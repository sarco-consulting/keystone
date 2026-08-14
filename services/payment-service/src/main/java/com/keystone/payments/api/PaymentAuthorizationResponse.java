package com.keystone.payments.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentAuthorizationResponse(
        UUID id,
        UUID orderId,
        BigDecimal amount,
        String currency,
        String status,
        String gatewayReference,
        Instant createdAt,
        Instant updatedAt) {
}
