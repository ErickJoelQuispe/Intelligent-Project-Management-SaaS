package com.epm.notification.infrastructure.messaging.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

/**
 * Unit tests for {@link KafkaTracingSupport} — consumer side (W3C trace context extraction).
 *
 * <p>Registers a real {@link OpenTelemetrySdk} with W3C propagator as global.
 * Verifies that a {@code traceparent} header manually added to Kafka record headers
 * is correctly parsed into an {@link Context} carrying the expected traceId and spanId.
 *
 * <p>No Kafka broker or Spring context required.
 */
class KafkaTracingExtractTest {

    /** A valid W3C traceparent with a known traceId and sampled flag. */
    private static final String KNOWN_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String KNOWN_SPAN_ID = "00f067aa0ba902b7";
    private static final String VALID_TRACEPARENT =
            "00-" + KNOWN_TRACE_ID + "-" + KNOWN_SPAN_ID + "-01";

    private OpenTelemetrySdk openTelemetrySdk;

    @BeforeEach
    void setUp() {
        // Reset global state before each test to avoid conflicts when other tests
        // in the same JVM process have already registered a GlobalOpenTelemetry instance.
        io.opentelemetry.api.GlobalOpenTelemetry.resetForTest();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(SpanExporter.composite()))
                .build();
        openTelemetrySdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();
    }

    @AfterEach
    void tearDown() {
        io.opentelemetry.api.GlobalOpenTelemetry.resetForTest();
        openTelemetrySdk.close();
    }

    @Test
    @DisplayName("extractTraceContext returns non-root Context when traceparent header is present")
    void extractTraceContext_withTraceparentHeader_returnsNonRootContext() {
        RecordHeaders headers = headersWithTraceparent(VALID_TRACEPARENT);

        Context extracted = KafkaTracingSupport.extractTraceContext(headers);

        assertThat(extracted)
                .as("Extracted context should not be the root context when traceparent is provided")
                .isNotSameAs(Context.root());
    }

    @Test
    @DisplayName("extractTraceContext extracts traceId and spanId from traceparent header")
    void extractTraceContext_withTraceparentHeader_extractsCorrectTraceAndSpanId() {
        RecordHeaders headers = headersWithTraceparent(VALID_TRACEPARENT);

        Context extracted = KafkaTracingSupport.extractTraceContext(headers);

        SpanContext spanCtx = io.opentelemetry.api.trace.Span.fromContext(extracted).getSpanContext();
        assertThat(spanCtx.isValid())
                .as("SpanContext should be valid when a proper traceparent is present")
                .isTrue();
        assertThat(spanCtx.getTraceId())
                .as("Extracted traceId must match the one in the traceparent header")
                .isEqualTo(KNOWN_TRACE_ID);
        assertThat(spanCtx.getSpanId())
                .as("Extracted spanId must match the parent spanId from the traceparent header")
                .isEqualTo(KNOWN_SPAN_ID);
        assertThat(spanCtx.getTraceFlags())
                .as("Sampling flag must be set (01 in traceparent flags)")
                .isEqualTo(TraceFlags.getSampled());
    }

    @Test
    @DisplayName("extractTraceContext returns invalid SpanContext when no traceparent header present")
    void extractTraceContext_withoutTraceparentHeader_returnsInvalidSpanContext() {
        RecordHeaders emptyHeaders = new RecordHeaders();

        Context extracted = KafkaTracingSupport.extractTraceContext(emptyHeaders);

        assertThat(extracted)
                .as("extractTraceContext must not return null — always returns a Context")
                .isNotNull();
        SpanContext spanCtx = io.opentelemetry.api.trace.Span.fromContext(extracted).getSpanContext();
        assertThat(spanCtx.isValid())
                .as("SpanContext should be invalid when no traceparent header is present")
                .isFalse();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private RecordHeaders headersWithTraceparent(String traceparentValue) {
        RecordHeaders headers = new RecordHeaders();
        headers.add("traceparent", traceparentValue.getBytes(StandardCharsets.UTF_8));
        return headers;
    }
}
