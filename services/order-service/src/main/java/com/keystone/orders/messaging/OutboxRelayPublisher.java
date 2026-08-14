package com.keystone.orders.messaging;

import com.keystone.events.outbox.AbstractOutboxRelayPublisher;
import com.keystone.events.outbox.TraceContextPropagation;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OutboxRelayPublisher extends AbstractOutboxRelayPublisher<OutboxMessage> {

    public OutboxRelayPublisher(OutboxMessageRepository repository, KafkaTemplate<String, String> kafkaTemplate, OpenTelemetry openTelemetry) {
        super(repository, kafkaTemplate, new TraceContextPropagation(openTelemetry));
    }
}
