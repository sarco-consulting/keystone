package com.keystone.orders.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOrderRequest(
        @NotBlank String customerId,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotEmpty List<@Valid OrderLineItemRequest> items) {
}
