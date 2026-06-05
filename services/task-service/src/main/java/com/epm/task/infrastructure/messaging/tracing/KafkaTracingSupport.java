package com.epm.task.infrastructure.messaging.tracing;

import java.nio.charset.StandardCharsets;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;

/**
 * Utility for injecting W3C trace context into outgoing Kafka producer records.
 *
 * <p>Uses {@link GlobalOpenTelemetry} which is initialized by the OTel auto-configuration
 * from {@code micrometer-tracing-bridge-otel} at runtime. In tests, it is configured
 * explicitly with an {@link io.opentelemetry.sdk.OpenTelemetrySdk}.
 *
 * <p>Usage: call {@link #injectTraceHeaders(ProducerRecord)} before sending a
 * {@link ProducerRecord} to Kafka. The W3C {@code traceparent} header is added to the
 * record's headers when an active span exists in the current {@link Context}.
 */
public final class KafkaTracingSupport {

    private static final TextMapSetter<Headers> HEADERS_SETTER =
            (headers, key, value) -> headers.add(key, value.getBytes(StandardCharsets.UTF_8));

    private KafkaTracingSupport() {}

    /**
     * Injects the current OTel trace context into a Kafka {@link ProducerRecord}'s headers.
     *
     * <p>When there is an active span in the current context, the W3C {@code traceparent}
     * (and optionally {@code tracestate}) headers are added to the record. When there is
     * no active span, the propagator is a no-op and no headers are added.
     *
     * @param record the outgoing Kafka record — headers are mutated in place
     */
    public static <K, V> void injectTraceHeaders(ProducerRecord<K, V> record) {
        GlobalOpenTelemetry.getPropagators()
                .getTextMapPropagator()
                .inject(Context.current(), record.headers(), HEADERS_SETTER);
    }
}
