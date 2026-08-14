package com.keystone.inventory.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReleaseInventoryRequest(@NotNull UUID orderId, @NotBlank String productId) {
}
