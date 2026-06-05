package com.epm.notification.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Verifies that the {@link LogstashEncoder} configuration used by the {@code docker}
 * Spring profile in {@code logback-spring.xml} produces valid structured JSON logs.
 *
 * <p>This is a pure Logback API test — no Spring context is required.
 * It encodes a log event directly and asserts the JSON structure, ensuring
 * the docker/prod profile will emit logs that satisfy the structured-logging spec:
 * - valid JSON object per log line
 * - {@code @timestamp} field present
 * - {@code message} field present
 * - {@code level} field present
 * - {@code service} custom field present with the configured service name
 * - MDC {@code traceId} and {@code spanId} fields included when set
 */
class StructuredLoggingTest {

    private static final String SERVICE_NAME = "notification-service";

    private LoggerContext loggerContext;
    private LogstashEncoder encoder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        loggerContext = new LoggerContext();
        loggerContext.start();
        loggerContext.putProperty("springAppName", SERVICE_NAME);

        encoder = new LogstashEncoder();
        encoder.setContext(loggerContext);

        // Match the customFields in logback-spring.xml docker profile
        encoder.setCustomFields("{\"service\":\"" + SERVICE_NAME + "\"}");

        // Match the fieldNames in logback-spring.xml:
        // <timestamp>@timestamp</timestamp>, <message>message</message>
        // <logger>logger</logger>, <thread>thread</thread>, <level>level</level>
        net.logstash.logback.fieldnames.LogstashFieldNames fieldNames =
                new net.logstash.logback.fieldnames.LogstashFieldNames();
        fieldNames.setTimestamp("@timestamp");
        fieldNames.setMessage("message");
        fieldNames.setLogger("logger");
        fieldNames.setThread("thread");
        fieldNames.setLevel("level");
        encoder.setFieldNames(fieldNames);

        encoder.start();
    }

    @AfterEach
    void tearDown() {
        encoder.stop();
        loggerContext.stop();
    }

    @Test
    void encode_producesValidJson() throws IOException {
        ILoggingEvent event = buildEvent(Level.INFO, "Hello structured logging");

        byte[] encoded = encoder.encode(event);
        String json = new String(encoded).trim();

        assertThat(json).isNotEmpty();
        JsonNode node = objectMapper.readTree(json);
        assertThat(node.isObject()).isTrue();
    }

    @Test
    void encode_containsTimestampField() throws IOException {
        ILoggingEvent event = buildEvent(Level.INFO, "timestamp test");

        String json = new String(encoder.encode(event)).trim();
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.has("@timestamp")).isTrue();
        assertThat(node.get("@timestamp").asText()).isNotBlank();
    }

    @Test
    void encode_containsMessageField() throws IOException {
        String expectedMessage = "Test message for structured logging";
        ILoggingEvent event = buildEvent(Level.INFO, expectedMessage);

        String json = new String(encoder.encode(event)).trim();
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.has("message")).isTrue();
        assertThat(node.get("message").asText()).isEqualTo(expectedMessage);
    }

    @Test
    void encode_containsLevelField() throws IOException {
        ILoggingEvent event = buildEvent(Level.WARN, "warn level test");

        String json = new String(encoder.encode(event)).trim();
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.has("level")).isTrue();
        assertThat(node.get("level").asText()).isEqualTo("WARN");
    }

    @Test
    void encode_containsServiceCustomField() throws IOException {
        ILoggingEvent event = buildEvent(Level.INFO, "service field test");

        String json = new String(encoder.encode(event)).trim();
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.has("service")).isTrue();
        assertThat(node.get("service").asText()).isEqualTo(SERVICE_NAME);
    }

    @Test
    void encode_withMdcTraceId_includesTraceIdInJson() throws IOException {
        String traceId = "abc123def456789012345678901234ab";
        String spanId = "1234567890abcdef";

        LoggerContext ctx = new LoggerContext();
        ctx.start();
        ctx.putProperty("springAppName", SERVICE_NAME);

        LogstashEncoder enc = new LogstashEncoder();
        enc.setContext(ctx);
        enc.setCustomFields("{\"service\":\"" + SERVICE_NAME + "\"}");
        enc.addIncludeMdcKeyName("traceId");
        enc.addIncludeMdcKeyName("spanId");
        enc.start();

        Map<String, String> mdcMap = new java.util.HashMap<>();
        mdcMap.put("traceId", traceId);
        mdcMap.put("spanId", spanId);

        LoggingEvent event = new LoggingEvent();
        event.setLoggerName("com.epm.notification.test");
        event.setLevel(Level.INFO);
        event.setMessage("trace test");
        event.setTimeStamp(Instant.now().toEpochMilli());
        event.setLoggerContext(ctx);
        event.setMDCPropertyMap(mdcMap);
        event.setThreadName(Thread.currentThread().getName());

        String json = new String(enc.encode(event)).trim();
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.has("traceId")).isTrue();
        assertThat(node.get("traceId").asText()).isEqualTo(traceId);
        assertThat(node.has("spanId")).isTrue();
        assertThat(node.get("spanId").asText()).isEqualTo(spanId);

        enc.stop();
        ctx.stop();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds a synthetic {@link LoggingEvent} without relying on SLF4J's static MDC adapter.
     * Uses the no-arg constructor and sets all fields manually to avoid NPE during encoding.
     */
    private ILoggingEvent buildEvent(Level level, String message) {
        return buildEvent(level, message, Collections.emptyMap());
    }

    private ILoggingEvent buildEvent(Level level, String message, Map<String, String> mdcMap) {
        LoggingEvent event = new LoggingEvent();
        event.setLoggerName("com.epm.notification.test");
        event.setLevel(level);
        event.setMessage(message);
        event.setTimeStamp(Instant.now().toEpochMilli());
        event.setLoggerContext(loggerContext);
        event.setMDCPropertyMap(mdcMap);
        event.setThreadName(Thread.currentThread().getName());
        return event;
    }
}
