package com.keystone.inventory.messaging;

import com.keystone.events.DomainEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "outbox_messages")
public class OutboxMessage extends com.keystone.events.outbox.OutboxMessage {

    protected OutboxMessage() {
        super();
    }

    public OutboxMessage(DomainEvent event, String topic, String messageKey, String payload, String traceparent) {
        super(event, topic, messageKey, payload, traceparent);
    }
}
