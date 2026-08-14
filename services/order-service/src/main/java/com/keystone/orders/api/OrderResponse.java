package com.keystone.orders.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String customerId,
        String status,
        String currency,
        BigDecimal totalAmount,
        List<OrderLineItemResponse> items,
        Instant createdAt,
        Instant updatedAt) {
}
