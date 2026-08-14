package com.keystone.events.outbox;

import io.opentelemetry.context.Scope;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls for unpublished outbox rows and relays them to Kafka. A row is only
 * marked published after the broker acknowledges the send — if the send
 * fails or times out, the row is left unpublished and retried on the next
 * poll, which is what gives the outbox pattern its at-least-once guarantee
 * (consumers are expected to be idempotent, see {@link ProcessedMessage}).
 *
 * {@code @Scheduled}/{@code @Transactional} live here, on the inherited
 * method, rather than being redeclared on each service's thin subclass —
 * Spring discovers and proxies them on the concrete bean regardless of which
 * class in the hierarchy declares them.
 */
public abstract class AbstractOutboxRelayPublisher<T extends OutboxMessage> {

    private static final Logger log = LoggerFactory.getLogger(AbstractOutboxRelayPublisher.class);

    private final OutboxMessageRepository<T> repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TraceContextPropagation traceContext;

    protected AbstractOutboxRelayPublisher(
            OutboxMessageRepository<T> repository,
            KafkaTemplate<String, String> kafkaTemplate,
            TraceContextPropagation traceContext) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.traceContext = traceContext;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:500}")
    @Transactional
    public void relay() {
        List<T> batch = repository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        int published = 0;
        // Entities are JPA-managed within this transaction, so markPublished()
        // is flushed by dirty checking on commit — no explicit save() needed.
        for (T message : batch) {
            // Restores the trace that was active when this row was written
            // (an HTTP request or an earlier saga step), so Kafka's producer
            // auto-instrumentation injects *that* context into the outgoing
            // headers instead of this scheduler thread's own unrelated one.
            try (Scope scope = traceContext.makeCurrent(message.getTraceparent())) {
                ProducerRecord<String, String> record =
                        new ProducerRecord<>(message.getTopic(), null, message.getMessageKey(), message.getPayload());
                record.headers().add(MessagingHeaders.EVENT_TYPE, message.getMessageType().getBytes(StandardCharsets.UTF_8));
                kafkaTemplate.send(record).get();
                message.markPublished();
                published++;
            } catch (Exception e) {
                log.error("Failed to relay outbox message {} to topic {}; will retry", message.getId(), message.getTopic(), e);
            }
        }
        if (published > 0) {
            log.info("Relayed {} outbox message(s) to Kafka", published);
        }
    }
}
