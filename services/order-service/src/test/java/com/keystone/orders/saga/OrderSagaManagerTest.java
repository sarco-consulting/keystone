package com.keystone.orders.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.keystone.events.DomainEvent;
import com.keystone.events.inventory.InventoryReleased;
import com.keystone.events.inventory.InventoryReservationFailed;
import com.keystone.events.inventory.InventoryReserved;
import com.keystone.events.payment.PaymentAuthorizationFailed;
import com.keystone.events.payment.PaymentAuthorized;
import com.keystone.orders.domain.Order;
import com.keystone.orders.domain.OrderLineItem;
import com.keystone.orders.domain.OrderStatus;
import com.keystone.orders.messaging.OutboxWriter;
import com.keystone.orders.persistence.OrderRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderSagaManagerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SagaStepRepository sagaStepRepository;

    @Mock
    private OutboxWriter outboxWriter;

    private OrderSagaManager sagaManager;
    private Order order;

    @BeforeEach
    void setUp() {
        sagaManager = new OrderSagaManager(orderRepository, sagaStepRepository, outboxWriter, new SimpleMeterRegistry());
        order = Order.create("customer-1", "USD", List.of(new OrderLineItem("sku-1", 2, new BigDecimal("10.00"))));
    }

    @Test
    void startWritesOrderCreatedAndReserveInventoryCommand() {
        sagaManager.start(order);

        verify(outboxWriter, times(2)).write(any(DomainEvent.class), any(), any());
        verify(sagaStepRepository, times(2)).save(any(SagaStep.class));
    }

    @Test
    void onInventoryReservedMarksOrderReservedAndRequestsPayment() {
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        sagaManager.onInventoryReserved(InventoryReserved.of(order.getId()));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.INVENTORY_RESERVED);
        verify(outboxWriter).write(any(DomainEvent.class), any(), any());
    }

    @Test
    void onInventoryReservationFailedCancelsOrderWithoutCompensation() {
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        sagaManager.onInventoryReservationFailed(InventoryReservationFailed.of(order.getId(), "out of stock"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(outboxWriter).write(any(DomainEvent.class), any(), any());
    }

    @Test
    void onPaymentAuthorizedConfirmsOrder() {
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        sagaManager.onPaymentAuthorized(PaymentAuthorized.of(order.getId(), "gw-1"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(outboxWriter).write(any(DomainEvent.class), any(), any());
    }

    @Test
    void onPaymentAuthorizationFailedTriggersCompensationWithoutCancellingYet() {
        order.markInventoryReserved();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        sagaManager.onPaymentAuthorizationFailed(PaymentAuthorizationFailed.of(order.getId(), "card declined"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.INVENTORY_RESERVED);
        verify(outboxWriter).write(any(DomainEvent.class), any(), any());
    }

    @Test
    void onInventoryReleasedFinalizesCancellation() {
        order.markInventoryReserved();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        sagaManager.onInventoryReleased(InventoryReleased.of(order.getId()));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(outboxWriter).write(any(DomainEvent.class), any(), any());
    }
}
