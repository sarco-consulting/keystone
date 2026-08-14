package com.keystone.payments.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keystone.events.DomainEvent;
import com.keystone.events.Topics;
import com.keystone.events.messaging.SagaMdc;
import com.keystone.events.outbox.MessagingHeaders;
import com.keystone.events.payment.AuthorizePaymentCommand;
import com.keystone.events.payment.PaymentAuthorizationFailed;
import com.keystone.events.payment.PaymentAuthorized;
import com.keystone.payments.application.PaymentService;
import com.keystone.payments.domain.PaymentAuthorization;
import com.keystone.payments.domain.PaymentStatus;
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
 * order-service's OrderResultListener. No REQUIRES_NEW gymnastics needed
 * here the way inventory's listener needs it: {@link PaymentService#authorize}
 * already returns DECLINED as a value rather than throwing (see
 * PaymentAuthorization's Javadoc), so there's no exception crossing a
 * transactional proxy boundary to worry about.
 */
@Component
public class PaymentCommandListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentCommandListener.class);

    private final ObjectMapper objectMapper;
    private final ProcessedMessageRepository processedMessageRepository;
    private final PaymentService paymentService;
    private final OutboxWriter outboxWriter;

    public PaymentCommandListener(
            ObjectMapper objectMapper,
            ProcessedMessageRepository processedMessageRepository,
            PaymentService paymentService,
            OutboxWriter outboxWriter) {
        this.objectMapper = objectMapper;
        this.processedMessageRepository = processedMessageRepository;
        this.paymentService = paymentService;
        this.outboxWriter = outboxWriter;
    }

    @KafkaListener(topics = Topics.PAYMENT_COMMANDS, groupId = "payment-service")
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
                case AuthorizePaymentCommand command -> handleAuthorize(command);
                default -> log.warn("No handler wired for command type {}", event.getClass().getSimpleName());
            }
            processedMessageRepository.save(new ProcessedMessage(event.eventId()));
        }
    }

    private void handleAuthorize(AuthorizePaymentCommand command) {
        PaymentAuthorization authorization = paymentService.authorize(command.orderId(), command.amount(), command.currency());
        if (authorization.getStatus() == PaymentStatus.AUTHORIZED) {
            outboxWriter.write(
                    PaymentAuthorized.of(command.orderId(), authorization.getGatewayReference()),
                    Topics.PAYMENT_EVENTS, command.orderId().toString());
            log.info("Payment authorized: {}", authorization.getGatewayReference());
        } else {
            outboxWriter.write(
                    PaymentAuthorizationFailed.of(command.orderId(), "Payment declined by gateway"),
                    Topics.PAYMENT_EVENTS, command.orderId().toString());
            log.info("Payment declined by gateway");
        }
    }

    private DomainEvent deserialize(String payload, String type) throws JsonProcessingException {
        return switch (type) {
            case "AuthorizePaymentCommand" -> objectMapper.readValue(payload, AuthorizePaymentCommand.class);
            default -> null;
        };
    }

    private String eventType(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(MessagingHeaders.EVENT_TYPE);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
}
