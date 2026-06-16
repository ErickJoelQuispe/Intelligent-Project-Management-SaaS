package com.epm.notification.infrastructure.adapter.in.messaging;

import java.time.Instant;
import java.util.UUID;

import com.epm.notification.application.usecase.NotificationApplicationService;
import com.epm.notification.domain.model.NotificationType;
import com.epm.notification.domain.port.in.CacheUserEmailUseCase;
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
 * Kafka consumer for {@code user.events} topic.
 *
 * <p>Handles:
 * <ul>
 *   <li>{@code UserRegistered} → populates {@code user_email_cache} via {@link CacheUserEmailUseCase}</li>
 *   <li>{@code MemberJoinedTeam} → creates {@code MEMBER_JOINED_TEAM} notification</li>
 *   <li>{@code MemberLeftTeam} → creates {@code MEMBER_LEFT_TEAM} notification</li>
 * </ul>
 *
 * <p><strong>Idempotency</strong>: uses an atomic {@code INSERT ... ON CONFLICT DO NOTHING}
 * ({@link ProcessedEventJpaRepository#claimEvent}) executed in the SAME transaction as the
 * business dispatch. {@code rows == 1} proceeds; {@code rows == 0} skips a duplicate. Because the
 * claim shares the consumer's transaction, a dispatch failure rolls the marker back too, so a
 * Kafka redelivery re-processes the event instead of permanently losing the notification.
 *
 * <p><strong>Poison-message handling (M1)</strong>: required fields are validated
 * up-front via {@link #requiredText} and {@link #requiredUuid}. A malformed payload
 * throws {@link MalformedEventException} which is caught here — the event is logged
 * and discarded without retrying. The idempotency claim is NOT made for poison messages.
 *
 * <p>Unknown event types are logged at WARN and skipped.
 */
@Component
public class UserEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserEventConsumer.class);
    private static final String TOPIC = "user.events";

    private final NotificationApplicationService notificationService;
    private final CacheUserEmailUseCase cacheUserEmailUseCase;
    private final ProcessedEventJpaRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    public UserEventConsumer(NotificationApplicationService notificationService,
            CacheUserEmailUseCase cacheUserEmailUseCase,
            ProcessedEventJpaRepository processedEventRepository,
            ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.cacheUserEmailUseCase = cacheUserEmailUseCase;
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
        String eventType;
        UUID tenantId;
        UUID userId;
        JsonNode payload;
        try {
            eventId = requiredText(root, "eventId");
            eventType = requiredText(root, "eventType");
            tenantId = requiredUuid(root, "tenantId");
            payload = root.get("payload");
            if (payload == null || payload.isNull()) {
                throw new MalformedEventException("Missing required field: payload");
            }
            userId = requiredUuid(payload, "userId");
        } catch (MalformedEventException | IllegalArgumentException e) {
            log.error("Malformed user event on topic {} — discarding (poison): {}", TOPIC, e.getMessage());
            return;
        }

        // ── Atomic idempotency claim (same transaction as dispatch) ────────────
        if (processedEventRepository.claimEvent(eventId, TOPIC, Instant.now()) == 0) {
            log.debug("Skipping duplicate user event: eventId={}", eventId);
            return;
        }

        // ── Business dispatch ──────────────────────────────────────────────────
        try {
            dispatch(eventType, tenantId, userId, payload);
            log.info("Processed user event: eventId={}, type={}", eventId, eventType);
        } catch (Exception e) {
            log.error("Failed to dispatch user event eventId={}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Failed to process user event", e);
        }
    }

    private void dispatch(String eventType, UUID tenantId, UUID userId, JsonNode payload) {
        switch (eventType) {
            case "UserRegistered" -> {
                // email is a required field for UserRegistered — extract safely
                String email;
                try {
                    email = requiredText(payload, "email");
                } catch (MalformedEventException e) {
                    log.error("UserRegistered event missing 'email' field — discarding (poison): {}",
                            e.getMessage());
                    return;
                }
                cacheUserEmailUseCase.cacheUserEmail(userId, tenantId, email);
            }
            case "MemberJoinedTeam" -> {
                String teamName = textOrDefault(payload, "teamName", "a team");
                notificationService.create(tenantId, userId,
                        NotificationType.MEMBER_JOINED_TEAM, userId,
                        "You joined " + teamName);
            }
            case "MemberLeftTeam" -> {
                String teamName = textOrDefault(payload, "teamName", "a team");
                notificationService.create(tenantId, userId,
                        NotificationType.MEMBER_LEFT_TEAM, userId,
                        "You left " + teamName);
            }
            default -> log.warn("Unknown user event type — skipping: eventType={}", eventType);
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

    /**
     * Extracts a required UUID field from a JSON node.
     *
     * @throws MalformedEventException if the field is absent, null, blank, or not a valid UUID
     */
    private static UUID requiredUuid(JsonNode node, String field) {
        String text = requiredText(node, field);
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            throw new MalformedEventException("Invalid UUID for field " + field + ": " + text, e);
        }
    }

    private String textOrDefault(JsonNode node, String field, String defaultValue) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return defaultValue;
    }
}
