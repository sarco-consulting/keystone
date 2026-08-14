package com.keystone.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderTest {

    @Test
    void createComputesTotalFromLineItems() {
        Order order = Order.create("customer-1", "USD", List.of(
                new OrderLineItem("sku-1", 2, new BigDecimal("10.00")),
                new OrderLineItem("sku-2", 1, new BigDecimal("5.50"))));

        assertThat(order.getTotalAmount()).isEqualByComparingTo("25.50");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getLineItems()).hasSize(2);
    }

    @Test
    void createRejectsEmptyLineItems() {
        assertThatThrownBy(() -> Order.create("customer-1", "USD", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
