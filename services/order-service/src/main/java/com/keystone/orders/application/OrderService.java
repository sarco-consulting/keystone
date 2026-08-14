package com.keystone.orders.application;

import com.keystone.orders.domain.Order;
import com.keystone.orders.domain.OrderLineItem;
import com.keystone.orders.domain.OrderNotFoundException;
import com.keystone.orders.persistence.OrderRepository;
import com.keystone.orders.saga.OrderSagaManager;
import com.keystone.orders.saga.SagaStep;
import com.keystone.orders.saga.SagaStepRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final SagaStepRepository sagaStepRepository;
    private final OrderSagaManager sagaManager;

    public OrderService(OrderRepository orderRepository, SagaStepRepository sagaStepRepository, OrderSagaManager sagaManager) {
        this.orderRepository = orderRepository;
        this.sagaStepRepository = sagaStepRepository;
        this.sagaManager = sagaManager;
    }

    // sagaManager.start() writes the OrderCreated announcement and the first
    // saga command (ReserveInventory) to the outbox in this same transaction
    // as the order insert — see OrderSagaManager and ADR-0002.
    @Transactional
    public Order createOrder(String customerId, String currency, List<OrderLineItem> items) {
        Order order = orderRepository.save(Order.create(customerId, currency, items));
        sagaManager.start(order);
        return order;
    }

    @Transactional(readOnly = true)
    public Order getOrder(UUID orderId) {
        return orderRepository.findByIdWithLineItems(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional(readOnly = true)
    public List<SagaStep> getTimeline(UUID orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new OrderNotFoundException(orderId);
        }
        return sagaStepRepository.findByOrderIdOrderByOccurredAtAsc(orderId);
    }
}
