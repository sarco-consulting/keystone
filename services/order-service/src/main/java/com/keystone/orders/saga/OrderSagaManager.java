package com.keystone.orders.saga;

import com.keystone.events.Topics;
import com.keystone.events.inventory.InventoryReleased;
import com.keystone.events.inventory.InventoryReservationFailed;
import com.keystone.events.inventory.InventoryReserved;
import com.keystone.events.inventory.ReleaseInventoryCommand;
import com.keystone.events.inventory.ReserveInventoryCommand;
import com.keystone.events.messaging.SagaMdc;
import com.keystone.events.order.OrderCancelled;
import com.keystone.events.order.OrderConfirmed;
import com.keystone.events.order.OrderCreated;
import com.keystone.events.payment.AuthorizePaymentCommand;
import com.keystone.events.payment.PaymentAuthorizationFailed;
import com.keystone.events.payment.PaymentAuthorized;
import com.keystone.orders.domain.Order;
import com.keystone.orders.domain.OrderNotFoundException;
import com.keystone.orders.messaging.OutboxWriter;
import com.keystone.orders.persistence.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The saga is orchestrated from here, not choreographed — see ADR-0001. Every
 * transition writes both a domain event (via the outbox, for other services /
 * observers) and a {@link SagaStep} (for the {@code /timeline} endpoint) in
 * the same transaction as the state change it represents.
 *
 * Release only happens as compensation for a failed payment in this saga —
 * there's no other reason to release inventory — so {@link #onInventoryReleased}
 * can finalize straight to CANCELLED without tracking *why* a release was
 * requested.
 */
@Service
public class OrderSagaManager {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaManager.class);
    private static final String COMPENSATION_REASON = "Payment authorization failed";

    private final OrderRepository orderRepository;
    private final SagaStepRepository sagaStepRepository;
    private final OutboxWriter outboxWriter;
    private final MeterRegistry meterRegistry;

    public OrderSagaManager(
            OrderRepository orderRepository,
            SagaStepRepository sagaStepRepository,
            OutboxWriter outboxWriter,
            MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.sagaStepRepository = sagaStepRepository;
        this.outboxWriter = outboxWriter;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void start(Order order) {
        // The saga's origin: runs on the HTTP request thread, not a Kafka
        // listener thread, so sagaId has to go into MDC here explicitly —
        // downstream listeners set it themselves from the message they
        // received before dispatching into this class.
        try (var ignored = SagaMdc.open(order.getId())) {
            outboxWriter.write(
                    OrderCreated.of(order.getId(), order.getCustomerId(), order.getTotalAmount(), order.getCurrency()),
                    Topics.ORDER_EVENTS, order.getId().toString());
            recordStep(order.getId(), SagaStepType.ORDER_CREATED, null);

            var items = order.getLineItems().stream()
                    .map(item -> new ReserveInventoryCommand.LineItem(item.getProductId(), item.getQuantity()))
                    .toList();
            outboxWriter.write(ReserveInventoryCommand.of(order.getId(), items), Topics.INVENTORY_COMMANDS, order.getId().toString());
            recordStep(order.getId(), SagaStepType.INVENTORY_RESERVE_REQUESTED, null);
        }
    }

    @Transactional
    public void onInventoryReserved(InventoryReserved event) {
        Order order = requireOrder(event.orderId());
        order.markInventoryReserved();
        recordStep(order.getId(), SagaStepType.INVENTORY_RESERVED, null);

        outboxWriter.write(
                AuthorizePaymentCommand.of(order.getId(), order.getTotalAmount(), order.getCurrency()),
                Topics.PAYMENT_COMMANDS, order.getId().toString());
        recordStep(order.getId(), SagaStepType.PAYMENT_AUTHORIZE_REQUESTED, null);
    }

    @Transactional
    public void onInventoryReservationFailed(InventoryReservationFailed event) {
        Order order = requireOrder(event.orderId());
        recordStep(order.getId(), SagaStepType.INVENTORY_RESERVATION_FAILED, event.reason());

        order.cancel();
        outboxWriter.write(OrderCancelled.of(order.getId(), event.reason()), Topics.ORDER_EVENTS, order.getId().toString());
        recordStep(order.getId(), SagaStepType.ORDER_CANCELLED, event.reason());
        meterRegistry.counter("keystone.orders.cancelled", "reason", "insufficient_stock").increment();
    }

    @Transactional
    public void onPaymentAuthorized(PaymentAuthorized event) {
        Order order = requireOrder(event.orderId());
        recordStep(order.getId(), SagaStepType.PAYMENT_AUTHORIZED, null);

        order.confirm();
        outboxWriter.write(OrderConfirmed.of(order.getId()), Topics.ORDER_EVENTS, order.getId().toString());
        recordStep(order.getId(), SagaStepType.ORDER_CONFIRMED, null);
        meterRegistry.counter("keystone.orders.confirmed").increment();
    }

    @Transactional
    public void onPaymentAuthorizationFailed(PaymentAuthorizationFailed event) {
        Order order = requireOrder(event.orderId());
        recordStep(order.getId(), SagaStepType.PAYMENT_AUTHORIZATION_FAILED, event.reason());

        outboxWriter.write(ReleaseInventoryCommand.of(order.getId()), Topics.INVENTORY_COMMANDS, order.getId().toString());
        recordStep(order.getId(), SagaStepType.INVENTORY_RELEASE_REQUESTED, event.reason());
        // Order stays INVENTORY_RESERVED until InventoryReleased confirms the
        // compensation completed — see onInventoryReleased.
    }

    @Transactional
    public void onInventoryReleased(InventoryReleased event) {
        Order order = requireOrder(event.orderId());
        recordStep(order.getId(), SagaStepType.INVENTORY_RELEASED, null);

        order.cancel();
        outboxWriter.write(OrderCancelled.of(order.getId(), COMPENSATION_REASON), Topics.ORDER_EVENTS, order.getId().toString());
        recordStep(order.getId(), SagaStepType.ORDER_CANCELLED, COMPENSATION_REASON);
        meterRegistry.counter("keystone.orders.cancelled", "reason", "payment_declined").increment();
    }

    private Order requireOrder(UUID orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private void recordStep(UUID orderId, SagaStepType step, String detail) {
        sagaStepRepository.save(new SagaStep(orderId, step, detail));
        // Always called from within a SagaMdc.open() scope (see #start and
        // OrderResultListener), so sagaId lands in every structured log line
        // this produces — the thing that makes "show me every log line for
        // order X across all three services" actually possible.
        log.info("Saga step: {}{}", step, detail != null ? " (" + detail + ")" : "");
    }
}
