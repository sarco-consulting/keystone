package com.keystone.events.messaging;

import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Shared consumer error-handling policy: retry a few times with backoff,
 * then give up and publish to {@code <topic>.DLT} rather than either
 * blocking the partition forever on a poison message or silently dropping
 * it. Spring Boot auto-detects a single {@code CommonErrorHandler} bean and
 * wires it into every {@code @KafkaListener} container automatically.
 */
public final class KafkaErrorHandlers {

    private KafkaErrorHandlers() {
    }

    public static DefaultErrorHandler withDeadLetterTopic(KafkaTemplate<String, String> kafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));

        var backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxElapsedTime(5_000L);

        return new DefaultErrorHandler(recoverer, backOff);
    }
}
