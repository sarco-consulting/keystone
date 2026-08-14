package com.keystone.orders.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keystone.events.DomainEvent;
import com.keystone.events.Topics;
import com.keystone.events.inventory.InventoryReleased;
import com.keystone.events.inventory.InventoryReservationFailed;
import com.keystone.events.inventory.InventoryReserved;
import com.keystone.events.messaging.SagaMdc;
import com.keystone.events.outbox.MessagingHeaders;
import com.keystone.events.payment.PaymentAuthorizationFailed;
import com.keystone.events.payment.PaymentAuthorized;
import com.keystone.orders.messaging.ProcessedMessage;
import com.keystone.orders.messaging.ProcessedMessageRepository;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Feeds inventory/payment result events back into the saga. Idempotent via
 * {@link ProcessedMessage}: Kafka delivery is at-least-once, and without this
 * check a redelivered InventoryReserved would issue a second
 * AuthorizePaymentCommand — the underlying domain operations are themselves
 * idempotent, but the *side effect of emitting another command* is not,
 * which is exactly what this dedupe check exists to prevent.
 */
@Component
public class OrderResultListener {

    private static final Logger log = LoggerFactory.getLogger(OrderResultListener.class);

    private final ObjectMapper objectMapper;
    private final ProcessedMessageRepository processedMessageRepository;
    private final OrderSagaManager sagaManager;

    public OrderResultListener(ObjectMapper objectMapper, ProcessedMessageRepository processedMessageRepository, OrderSagaManager sagaManager) {
        this.objectMapper = objectMapper;
        this.processedMessageRepository = processedMessageRepository;
        this.sagaManager = sagaManager;
    }

    @KafkaListener(topics = {Topics.INVENTORY_EVENTS, Topics.PAYMENT_EVENTS}, groupId = "order-service")
    @Transactional
    public void onMessage(ConsumerRecord<String, String> record) throws JsonProcessingException {
        String type = eventType(record);
        DomainEvent event = deserialize(record.value(), type);
        if (event == null) {
            log.warn("Unrecognized event type '{}' on topic {}; ignoring", type, record.topic());
            return;
        }

        if (processedMessageRepository.existsById(event.eventId())) {
            log.debug("Skipping already-processed event {} ({})", event.eventId(), type);
            return;
        }

        try (var ignored = SagaMdc.open(event.sagaId())) {
            dispatch(event);
            processedMessageRepository.save(new ProcessedMessage(event.eventId()));
        }
    }

    private void dispatch(DomainEvent event) {
        switch (event) {
            case InventoryReserved e -> sagaManager.onInventoryReserved(e);
            case InventoryReservationFailed e -> sagaManager.onInventoryReservationFailed(e);
            case InventoryReleased e -> sagaManager.onInventoryReleased(e);
            case PaymentAuthorized e -> sagaManager.onPaymentAuthorized(e);
            case PaymentAuthorizationFailed e -> sagaManager.onPaymentAuthorizationFailed(e);
            default -> log.warn("No saga handler wired for event type {}", event.getClass().getSimpleName());
        }
    }

    private DomainEvent deserialize(String payload, String type) throws JsonProcessingException {
        return switch (type) {
            case "InventoryReserved" -> objectMapper.readValue(payload, InventoryReserved.class);
            case "InventoryReservationFailed" -> objectMapper.readValue(payload, InventoryReservationFailed.class);
            case "InventoryReleased" -> objectMapper.readValue(payload, InventoryReleased.class);
            case "PaymentAuthorized" -> objectMapper.readValue(payload, PaymentAuthorized.class);
            case "PaymentAuthorizationFailed" -> objectMapper.readValue(payload, PaymentAuthorizationFailed.class);
            default -> null;
        };
    }

    private String eventType(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(MessagingHeaders.EVENT_TYPE);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
}
