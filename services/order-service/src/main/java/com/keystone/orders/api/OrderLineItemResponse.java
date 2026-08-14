package com.keystone.orders.api;

import java.math.BigDecimal;

public record OrderLineItemResponse(
        String productId,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal) {
}
