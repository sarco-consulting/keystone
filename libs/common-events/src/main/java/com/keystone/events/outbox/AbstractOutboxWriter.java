package com.keystone.events.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keystone.events.DomainEvent;

/**
 * Call from inside the same {@code @Transactional} method as the business
 * write it's announcing — that's what makes the outbox pattern work. Calling
 * it after the transaction commits defeats the point.
 */
public abstract class AbstractOutboxWriter<T extends OutboxMessage> {

    private final OutboxMessageRepository<T> repository;
    private final ObjectMapper objectMapper;
    private final OutboxMessageFactory<T> factory;
    private final TraceContextPropagation traceContext;

    protected AbstractOutboxWriter(
            OutboxMessageRepository<T> repository,
            ObjectMapper objectMapper,
            OutboxMessageFactory<T> factory,
            TraceContextPropagation traceContext) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.factory = factory;
        this.traceContext = traceContext;
    }

    public void write(DomainEvent event, String topic, String messageKey) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            // Captured here, not at relay time: this runs inside the caller's
            // active span (an HTTP request or a Kafka listener's), which the
            // relay's scheduler thread has no way to see later. See
            // TraceContextPropagation.
            String traceparent = traceContext.captureCurrent();
            repository.save(factory.create(event, topic, messageKey, payload, traceparent));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event " + event.getClass().getSimpleName(), e);
        }
    }
}
