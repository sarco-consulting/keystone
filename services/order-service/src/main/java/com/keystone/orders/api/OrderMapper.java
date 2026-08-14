package com.keystone.orders.api;

import com.keystone.orders.domain.Order;
import com.keystone.orders.domain.OrderLineItem;
import com.keystone.orders.saga.SagaStep;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

final class OrderMapper {

    private OrderMapper() {
    }

    static OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus().name(),
                order.getCurrency(),
                order.getTotalAmount(),
                order.getLineItems().stream().map(OrderMapper::toResponse).toList(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private static OrderLineItemResponse toResponse(OrderLineItem item) {
        BigDecimal lineTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return new OrderLineItemResponse(item.getProductId(), item.getQuantity(), item.getUnitPrice(), lineTotal);
    }

    static OrderTimelineResponse toTimelineResponse(UUID orderId, List<SagaStep> steps) {
        return new OrderTimelineResponse(orderId, steps.stream().map(OrderMapper::toResponse).toList());
    }

    private static SagaStepResponse toResponse(SagaStep step) {
        return new SagaStepResponse(step.getStep().name(), step.getDetail(), step.getOccurredAt());
    }
}
