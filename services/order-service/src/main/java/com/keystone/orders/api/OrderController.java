package com.keystone.orders.api;

import com.keystone.orders.application.OrderService;
import com.keystone.orders.domain.Order;
import com.keystone.orders.domain.OrderLineItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
@Tag(name = "Orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "Place an order", description = "Creates an order in PENDING status.")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        List<OrderLineItem> items = request.items().stream()
                .map(item -> new OrderLineItem(item.productId(), item.quantity(), item.unitPrice()))
                .toList();

        Order order = orderService.createOrder(request.customerId(), request.currency(), items);

        return ResponseEntity.created(URI.create("/orders/" + order.getId())).body(OrderMapper.toResponse(order));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an order by id")
    public OrderResponse getOrder(@PathVariable UUID id) {
        return OrderMapper.toResponse(orderService.getOrder(id));
    }

    @GetMapping("/{id}/timeline")
    @Operation(summary = "Get the full saga step history for an order", description = "Watch a distributed transaction unfold in one call.")
    public OrderTimelineResponse getTimeline(@PathVariable UUID id) {
        return OrderMapper.toTimelineResponse(id, orderService.getTimeline(id));
    }
}
