package com.epm.notification.infrastructure.adapter.in.messaging;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import com.epm.notification.domain.port.out.EmailPort;
import com.epm.notification.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import com.epm.notification.infrastructure.messaging.tracing.KafkaTracingSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer for {@code user.invitation.sent} topic.
 *
 * <p>Handles:
 * <ul>
 *   <li>{@code InvitationSent} → sends an invitation email via {@link EmailPort}
 *       using the {@code invitation-v1} Thymeleaf template</li>
 * </ul>
 *
 * <p><strong>Idempotency</strong>: uses an atomic {@code INSERT ... ON CONFLICT DO NOTHING}
 * ({@link ProcessedEventJpaRepository#claimEvent}) executed in the SAME transaction as the
 * business dispatch. {@code rows == 1} proceeds; {@code rows == 0} skips a duplicate.
 *
 * <p><strong>Poison-message handling (M1)</strong>: required fields ({@code eventId},
 * {@code payload.email}, {@code payload.token}) are validated up-front via
 * {@link #requiredText}. A malformed payload throws {@link MalformedEventException}
 * which is caught here — the event is logged and discarded without retrying.
 *
 * <p><strong>SMTP failures</strong>: logged at ERROR and silently swallowed so the
 * Kafka offset is committed (dev-env only — no retry infrastructure available).
 */
@Component
public class InvitationSentConsumer {

    private static final Logger log = LoggerFactory.getLogger(InvitationSentConsumer.class);
    private static final String TOPIC = "user.invitation.sent";
    private static final String EMAIL_SUBJECT = "You've been invited to join a workspace";
    private static final String EMAIL_TEMPLATE = "invitation-v1";
    private static final String ACCEPT_URL_BASE = "http://localhost:4200/accept-invitation";

    private final EmailPort emailPort;
    private final ProcessedEventJpaRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    public InvitationSentConsumer(EmailPort emailPort,
            ProcessedEventJpaRepository processedEventRepository,
            ObjectMapper objectMapper) {
        this.emailPort = emailPort;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = TOPIC, groupId = "notification-group")
    @Transactional
    public void consume(ConsumerRecord<String, String> record) {
        Context traceContext = KafkaTracingSupport.extractTraceContext(record.headers());
        try (Scope ignored = traceContext.makeCurrent()) {
            processRecord(record.value());
        }
    }

    private void processRecord(String message) {
        // ── Up-front parse (M1 poison guard) ─────────────────────────────────
        JsonNode root;
        try {
            root = objectMapper.readTree(message);
        } catch (JsonProcessingException e) {
            log.error("Malformed JSON on topic {} — discarding (poison)", TOPIC, e);
            return;
        }

        String eventId;
        String email;
        String token;
        try {
            eventId = requiredText(root, "eventId");
            JsonNode payload = root.get("payload");
            if (payload == null || payload.isNull()) {
                throw new MalformedEventException("Missing required field: payload");
            }
            email = requiredText(payload, "email");
            token = requiredText(payload, "token");
        } catch (MalformedEventException | IllegalArgumentException e) {
            log.error("Malformed InvitationSent event on topic {} — discarding (poison): {}",
                    TOPIC, e.getMessage());
            return;
        }

        // ── Atomic idempotency claim (same transaction as dispatch) ────────────
        if (processedEventRepository.claimEvent(eventId, TOPIC, Instant.now()) == 0) {
            log.debug("Skipping duplicate InvitationSent event: eventId={}", eventId);
            return;
        }

        // ── Build accept URL ───────────────────────────────────────────────────
        String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
        String acceptUrl = ACCEPT_URL_BASE + "?token=" + token + "&email=" + encodedEmail;

        // ── Send invitation email (best-effort — SMTP failure is logged, not retried) ──
        try {
            emailPort.send(email, EMAIL_SUBJECT, EMAIL_TEMPLATE,
                    Map.of("acceptUrl", acceptUrl, "email", email));
            log.info("Invitation email sent: eventId={}, to={}", eventId, email);
        } catch (Exception e) {
            log.error("Failed to send invitation email for eventId={}, to={}: {}",
                    eventId, email, e.getMessage(), e);
            // Swallow: commit offset anyway — dev env, no retry infrastructure
        }
    }

    // ── Field helpers (M1 poison guard) ──────────────────────────────────────

    /**
     * Extracts a required non-blank text field from a JSON node.
     *
     * @throws MalformedEventException if the field is absent, null, or blank
     */
    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new MalformedEventException("Missing required field: " + field);
        }
        String text = value.asText(null);
        if (text == null || text.isBlank()) {
            throw new MalformedEventException("Empty required field: " + field);
        }
        return text;
    }
}
