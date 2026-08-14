package com.keystone.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Marker contract for events and commands exchanged between Keystone services.
 * {@code sagaId} ties every message belonging to the same order saga together
 * across services, for correlation in logs and traces.
 */
public interface DomainEvent {

    UUID eventId();

    UUID sagaId();

    Instant occurredAt();
}
