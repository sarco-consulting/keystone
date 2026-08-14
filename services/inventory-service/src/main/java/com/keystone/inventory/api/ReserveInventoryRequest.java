package com.keystone.inventory.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record ReserveInventoryRequest(
        @NotNull UUID orderId,
        @NotBlank String productId,
        @Positive int quantity) {
}
