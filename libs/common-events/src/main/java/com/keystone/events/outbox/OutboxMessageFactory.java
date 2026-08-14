package com.keystone.events.outbox;

import com.keystone.events.DomainEvent;

@FunctionalInterface
public interface OutboxMessageFactory<T extends OutboxMessage> {

    T create(DomainEvent event, String topic, String messageKey, String payload, String traceparent);
}
