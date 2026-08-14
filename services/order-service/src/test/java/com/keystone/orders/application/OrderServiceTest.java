package com.keystone.orders.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.keystone.orders.domain.Order;
import com.keystone.orders.domain.OrderLineItem;
import com.keystone.orders.domain.OrderNotFoundException;
import com.keystone.orders.persistence.OrderRepository;
import com.keystone.orders.saga.OrderSagaManager;
import com.keystone.orders.saga.SagaStepRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SagaStepRepository sagaStepRepository;

    @Mock
    private OrderSagaManager sagaManager;

    @Test
    void createOrderPersistsAndReturnsTheOrder() {
        OrderService service = new OrderService(orderRepository, sagaStepRepository, sagaManager);
        List<OrderLineItem> items = List.of(new OrderLineItem("sku-1", 1, new BigDecimal("9.99")));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order created = service.createOrder("customer-1", "USD", items);

        assertThat(created.getCustomerId()).isEqualTo("customer-1");
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void createOrderStartsTheSaga() {
        OrderService service = new OrderService(orderRepository, sagaStepRepository, sagaManager);
        List<OrderLineItem> items = List.of(new OrderLineItem("sku-1", 1, new BigDecimal("9.99")));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order created = service.createOrder("customer-1", "USD", items);

        verify(sagaManager).start(created);
    }

    @Test
    void getOrderThrowsWhenMissing() {
        OrderService service = new OrderService(orderRepository, sagaStepRepository, sagaManager);
        UUID missingId = UUID.randomUUID();
        when(orderRepository.findByIdWithLineItems(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrder(missingId)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void getTimelineThrowsWhenOrderMissing() {
        OrderService service = new OrderService(orderRepository, sagaStepRepository, sagaManager);
        UUID missingId = UUID.randomUUID();
        when(orderRepository.existsById(missingId)).thenReturn(false);

        assertThatThrownBy(() -> service.getTimeline(missingId)).isInstanceOf(OrderNotFoundException.class);
    }
}
