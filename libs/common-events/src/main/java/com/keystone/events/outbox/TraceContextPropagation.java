package com.keystone.events.outbox;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Bridges OpenTelemetry trace context across the transactional outbox's
 * async boundary.
 *
 * Auto-instrumentation propagates whatever trace context is <em>current</em>
 * at the moment {@code KafkaTemplate.send()} is called — but the outbox
 * relay runs on a scheduler thread, seconds after (and with no ambient
 * relationship to) the original request. Without this, every relayed
 * message starts a brand-new trace instead of continuing the one that
 * triggered it, and the promise of "watch a trace span the order across all
 * three services" quietly doesn't hold.
 *
 * The fix: capture the current trace as a W3C traceparent string on the
 * outbox row when it's written (still inside the original request/listener's
 * span), then restore it as the current context around the relay's send()
 * call. Auto-instrumentation does the rest — it injects whatever context is
 * current into the outgoing Kafka headers, and the consumer side already
 * extracts an incoming traceparent header into a proper parent span without
 * any changes needed there.
 *
 * Takes an injected {@link OpenTelemetry} rather than reaching for
 * {@code GlobalOpenTelemetry.get()}: the Spring Boot starter exposes the
 * configured SDK as a bean instead of registering it globally, so the
 * static accessor silently returns a no-op instance whose propagator
 * injects nothing — a real, easy-to-miss trap.
 */
public final class TraceContextPropagation {

    private static final String TRACEPARENT_KEY = "traceparent";

    private static final TextMapSetter<Map<String, String>> SETTER = Map::put;

    private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };

    private final TextMapPropagator propagator;

    public TraceContextPropagation(OpenTelemetry openTelemetry) {
        this.propagator = openTelemetry.getPropagators().getTextMapPropagator();
    }

    /** Captures the current trace as a single W3C traceparent string, for storage on the outbox row. */
    public String captureCurrent() {
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(Context.current(), carrier, SETTER);
        return carrier.get(TRACEPARENT_KEY);
    }

    /** Restores a stored traceparent as the current context for the duration of a try-with-resources block. */
    public Scope makeCurrent(String traceparent) {
        if (traceparent == null) {
            return Context.current().makeCurrent();
        }
        Context extracted = propagator.extract(Context.root(), Collections.singletonMap(TRACEPARENT_KEY, traceparent), GETTER);
        return extracted.makeCurrent();
    }
}
