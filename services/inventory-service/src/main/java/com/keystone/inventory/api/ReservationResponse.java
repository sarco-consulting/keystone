package com.keystone.inventory.api;

import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID orderId,
        String productId,
        int quantity,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
