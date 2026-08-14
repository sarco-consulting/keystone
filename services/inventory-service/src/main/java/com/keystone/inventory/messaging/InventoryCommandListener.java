package com.keystone.inventory.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keystone.events.DomainEvent;
import com.keystone.events.Topics;
import com.keystone.events.inventory.InventoryReleased;
import com.keystone.events.inventory.InventoryReservationFailed;
import com.keystone.events.inventory.InventoryReserved;
import com.keystone.events.inventory.ReleaseInventoryCommand;
import com.keystone.events.inventory.ReserveInventoryCommand;
import com.keystone.events.messaging.SagaMdc;
import com.keystone.events.outbox.MessagingHeaders;
import com.keystone.inventory.application.InventoryService;
import com.keystone.inventory.application.ReservationItem;
import com.keystone.inventory.domain.InsufficientStockException;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent via {@link ProcessedMessage} — see the equivalent note on
 * order-service's OrderResultListener for why the dedupe check matters even
 * though the underlying domain operations are themselves idempotent.
 */
@Component
public class InventoryCommandListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryCommandListener.class);

    private final ObjectMapper objectMapper;
    private final ProcessedMessageRepository processedMessageRepository;
    private final InventoryService inventoryService;
    private final OutboxWriter outboxWriter;

    public InventoryCommandListener(
            ObjectMapper objectMapper,
            ProcessedMessageRepository processedMessageRepository,
            InventoryService inventoryService,
            OutboxWriter outboxWriter) {
        this.objectMapper = objectMapper;
        this.processedMessageRepository = processedMessageRepository;
        this.inventoryService = inventoryService;
        this.outboxWriter = outboxWriter;
    }

    @KafkaListener(topics = Topics.INVENTORY_COMMANDS, groupId = "inventory-service")
    @Transactional
    public void onMessage(ConsumerRecord<String, String> record) throws JsonProcessingException {
        String type = eventType(record);
        DomainEvent event = deserialize(record.value(), type);
        if (event == null) {
            log.warn("Unrecognized command type '{}' on topic {}; ignoring", type, record.topic());
            return;
        }

        if (processedMessageRepository.existsById(event.eventId())) {
            log.debug("Skipping already-processed command {} ({})", event.eventId(), type);
            return;
        }

        try (var ignored = SagaMdc.open(event.sagaId())) {
            switch (event) {
                case ReserveInventoryCommand command -> handleReserve(command);
                case ReleaseInventoryCommand command -> handleRelease(command);
                default -> log.warn("No handler wired for command type {}", event.getClass().getSimpleName());
            }
            processedMessageRepository.save(new ProcessedMessage(event.eventId()));
        }
    }

    private void handleReserve(ReserveInventoryCommand command) {
        var items = command.items().stream()
                .map(item -> new ReservationItem(item.productId(), item.quantity()))
                .toList();
        try {
            inventoryService.reserveAll(command.orderId(), items);
            outboxWriter.write(InventoryReserved.of(command.orderId()), Topics.INVENTORY_EVENTS, command.orderId().toString());
            log.info("Reserved {} item(s) for order", items.size());
        } catch (InsufficientStockException e) {
            outboxWriter.write(
                    InventoryReservationFailed.of(command.orderId(), e.getMessage()),
                    Topics.INVENTORY_EVENTS, command.orderId().toString());
            log.info("Reservation failed: {}", e.getMessage());
        }
    }

    private void handleRelease(ReleaseInventoryCommand command) {
        inventoryService.releaseAll(command.orderId());
        outboxWriter.write(InventoryReleased.of(command.orderId()), Topics.INVENTORY_EVENTS, command.orderId().toString());
        log.info("Released reservation (compensation)");
    }

    private DomainEvent deserialize(String payload, String type) throws JsonProcessingException {
        return switch (type) {
            case "ReserveInventoryCommand" -> objectMapper.readValue(payload, ReserveInventoryCommand.class);
            case "ReleaseInventoryCommand" -> objectMapper.readValue(payload, ReleaseInventoryCommand.class);
            default -> null;
        };
    }

    private String eventType(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(MessagingHeaders.EVENT_TYPE);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
}
