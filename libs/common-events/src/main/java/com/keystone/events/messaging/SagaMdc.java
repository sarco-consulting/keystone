package com.keystone.events.messaging;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * Puts {@code sagaId} into the logging MDC for the duration of a saga step,
 * so every log line — across all three services — for a given order can be
 * found by filtering on one value, without needing a tracing UI open.
 *
 * <pre>{@code try (var ignored = SagaMdc.open(event.sagaId())) { ... }}</pre>
 */
public final class SagaMdc {

    private static final String KEY = "sagaId";

    private SagaMdc() {
    }

    public static Scope open(UUID sagaId) {
        MDC.put(KEY, sagaId.toString());
        return () -> MDC.remove(KEY);
    }

    /** {@code close()} deliberately declares no checked exception, unlike {@link AutoCloseable}. */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
