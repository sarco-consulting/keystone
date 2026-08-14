package com.keystone.inventory.messaging;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "processed_messages")
public class ProcessedMessage extends com.keystone.events.outbox.ProcessedMessage {

    protected ProcessedMessage() {
        super();
    }

    public ProcessedMessage(UUID eventId) {
        super(eventId);
    }
}
