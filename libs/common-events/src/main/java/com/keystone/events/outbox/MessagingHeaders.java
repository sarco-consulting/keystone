package com.keystone.events.outbox;

/**
 * Several topics (e.g. inventory.commands) carry more than one message type,
 * so consumers need a discriminator to know which record to deserialize a
 * given payload into. Carried as a Kafka header rather than a field inside
 * the JSON payload, so the payload itself stays a clean, direct
 * serialization of the event/command record.
 */
public final class MessagingHeaders {

    private MessagingHeaders() {
    }

    public static final String EVENT_TYPE = "event-type";
}
