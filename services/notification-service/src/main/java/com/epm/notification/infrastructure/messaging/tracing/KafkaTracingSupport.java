package com.epm.notification.infrastructure.messaging.tracing;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

/**
 * Utility for extracting W3C trace context from incoming Kafka consumer record headers.
 *
 * <p>Uses {@link GlobalOpenTelemetry} which is initialized by the OTel auto-configuration
 * from {@code micrometer-tracing-bridge-otel} at runtime. In tests, it is configured
 * explicitly with an {@link io.opentelemetry.sdk.OpenTelemetrySdk}.
 *
 * <p>Usage: call {@link #extractTraceContext(Headers)} at the start of each
 * {@link org.springframework.kafka.annotation.KafkaListener} method, then wrap
 * the processing logic with the extracted context using
 * {@code try (Scope scope = extractedContext.makeCurrent()) { ... }}.
 */
public final class KafkaTracingSupport {

    private static final TextMapGetter<Headers> HEADERS_GETTER = new TextMapGetter<>() {

        @Override
        public Iterable<String> keys(Headers headers) {
            List<String> keys = new ArrayList<>();
            headers.forEach(h -> keys.add(h.key()));
            return keys;
        }

        @Override
        public String get(Headers headers, String key) {
            Header header = headers.lastHeader(key);
            return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
        }
    };

    private KafkaTracingSupport() {}

    /**
     * Extracts the W3C trace context from Kafka record {@link Headers}.
     *
     * <p>Parses the {@code traceparent} (and optionally {@code tracestate}) header
     * and returns a {@link Context} carrying the remote span context. When no
     * {@code traceparent} header is present, the returned context has an invalid
     * {@link io.opentelemetry.api.trace.SpanContext} (i.e., the propagation is a no-op).
     *
     * @param headers the Kafka record headers
     * @return a {@link Context} with the extracted remote span, or the current context
     *         with no valid span if the header is missing or malformed
     */
    public static Context extractTraceContext(Headers headers) {
        return GlobalOpenTelemetry.getPropagators()
                .getTextMapPropagator()
                .extract(Context.current(), headers, HEADERS_GETTER);
    }
}
