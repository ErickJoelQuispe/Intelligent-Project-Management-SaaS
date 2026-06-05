package com.epm.task.infrastructure.messaging.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link KafkaTracingSupport} — producer side.
 *
 * <p>Registers a real {@link OpenTelemetrySdk} with the W3C trace propagator as global.
 * Creates real spans via {@link Tracer} to verify the W3C {@code traceparent} header is
 * injected into a {@link ProducerRecord} when a span is active.
 *
 * <p>No Kafka broker or Spring context required.
 */
class KafkaTracingHeaderTest {

    private OpenTelemetrySdk openTelemetrySdk;
    private Tracer tracer;

    @BeforeEach
    void setUp() {
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(SpanExporter.composite()))
                .build();

        openTelemetrySdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();

        tracer = openTelemetrySdk.getTracer("kafka-tracing-test");
    }

    @AfterEach
    void tearDown() {
        io.opentelemetry.api.GlobalOpenTelemetry.resetForTest();
        openTelemetrySdk.close();
    }

    @Test
    @DisplayName("injectTraceHeaders adds traceparent header when an active span is present")
    void injectTraceHeaders_withActiveSpan_addstraceparentHeader() {
        ProducerRecord<String, String> record = new ProducerRecord<>("task.events", "key", "payload");

        Span span = tracer.spanBuilder("test-span").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            KafkaTracingSupport.injectTraceHeaders(record);
        } finally {
            span.end();
        }

        Header traceparentHeader = record.headers().lastHeader("traceparent");
        assertThat(traceparentHeader)
                .as("traceparent header should be present when a valid span is active")
                .isNotNull();
        assertThat(new String(traceparentHeader.value()))
                .as("traceparent value should follow W3C format: 00-<traceId>-<spanId>-<flags>")
                .startsWith("00-");
    }

    @Test
    @DisplayName("injectTraceHeaders does not inject traceparent when no active span")
    void injectTraceHeaders_withNoActiveSpan_doesNotAddTraceparentHeader() {
        ProducerRecord<String, String> record = new ProducerRecord<>("task.events", "key", "payload");

        // No span started — Context.current() has an invalid SpanContext
        KafkaTracingSupport.injectTraceHeaders(record);

        // W3C propagator only injects traceparent when span has a valid, sampled SpanContext
        Header traceparentHeader = record.headers().lastHeader("traceparent");
        assertThat(traceparentHeader)
                .as("No traceparent header should be injected when there is no active valid span")
                .isNull();
    }

    @Test
    @DisplayName("injectTraceHeaders traceparent contains the active span's traceId")
    void injectTraceHeaders_withActiveSpan_traceparentContainsSpanTraceId() {
        ProducerRecord<String, String> record = new ProducerRecord<>("task.events", "key", "payload");

        Span span = tracer.spanBuilder("root-span").startSpan();
        String expectedTraceId = span.getSpanContext().getTraceId();

        try (Scope ignored = span.makeCurrent()) {
            KafkaTracingSupport.injectTraceHeaders(record);
        } finally {
            span.end();
        }

        Header traceparentHeader = record.headers().lastHeader("traceparent");
        assertThat(traceparentHeader).isNotNull();
        // W3C traceparent: 00-{32-char-traceId}-{16-char-spanId}-{flags}
        assertThat(new String(traceparentHeader.value()))
                .as("traceparent must contain the active span's traceId")
                .contains(expectedTraceId);
    }
}
