package com.keystone.inventory.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keystone.events.outbox.AbstractOutboxWriter;
import com.keystone.events.outbox.TraceContextPropagation;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.stereotype.Component;

@Component
public class OutboxWriter extends AbstractOutboxWriter<OutboxMessage> {

    public OutboxWriter(OutboxMessageRepository repository, ObjectMapper objectMapper, OpenTelemetry openTelemetry) {
        super(repository, objectMapper, OutboxMessage::new, new TraceContextPropagation(openTelemetry));
    }
}
