package com.keystone.orders.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record OrderLineItemRequest(
        @NotBlank String productId,
        @Positive int quantity,
        @NotNull @PositiveOrZero BigDecimal unitPrice) {
}
