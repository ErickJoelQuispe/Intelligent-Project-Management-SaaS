package com.epm.notification.infrastructure.adapter.in.messaging;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.epm.notification.application.usecase.NotificationApplicationService;
import com.epm.notification.domain.model.Notification;
import com.epm.notification.domain.model.NotificationType;
import com.epm.notification.domain.port.out.NotificationPushPort;
import com.epm.notification.infrastructure.adapter.in.web.NotificationResponse;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Kafka consumer for project domain event topics.
 *
 * <p>Listens to the granular topics published by project-service:
 * <ul>
 *   <li>{@code project.project.created} — eventType: {@code ProjectCreated}</li>
 *   <li>{@code project.project.archived} — eventType: {@code ProjectArchived}</li>
 *   <li>{@code project.team.assigned} — eventType: {@code TeamAssignedToProject}</li>
 * </ul>
 *
 * <p>Dispatches by {@code eventType} field in the JSON envelope and creates
 * in-app notifications for relevant project domain events.
 *
 * <p><strong>Idempotency</strong>: uses an atomic {@code INSERT ... ON CONFLICT DO NOTHING}
 * ({@link ProcessedEventJpaRepository#claimEvent}) executed in the SAME transaction as the
 * business dispatch. The claim uses {@code record.topic()} (the actual topic from the
 * {@link ConsumerRecord}) so that events from different topics are tracked independently.
 * {@code rows == 1} means this delivery won the claim and proceeds;
 * {@code rows == 0} means the event was already processed and is skipped. Because the claim
 * shares the consumer's transaction, a dispatch failure rolls the marker back too, so a Kafka
 * redelivery re-processes the event instead of permanently losing the notification.
 *
 * <p><strong>Poison-message handling (M1)</strong>: required fields are validated
 * up-front via {@link #requiredText} and {@link #requiredUuid}. A malformed payload
 * throws {@link MalformedEventException} which is caught here — the event is logged
 * and discarded (no retry, no infinite loop). The idempotency claim is NOT made for
 * poison messages; a later valid redelivery (if ever) must not be silently skipped.
 *
 * <p>Handled event types:
 * <ul>
 *   <li>{@code ProjectCreated} → notify ownerId with type {@code PROJECT_CREATED}</li>
 *   <li>{@code TeamAssignedToProject} → notify each memberId with {@code TEAM_ASSIGNED_TO_PROJECT}</li>
 *   <li>{@code ProjectArchived} → notify ownerId with type {@code PROJECT_ARCHIVED}</li>
 * </ul>
 */
@Component
public class ProjectEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProjectEventConsumer.class);
    private static final String TOPIC_PROJECT_CREATED  = "project.project.created";
    private static final String TOPIC_PROJECT_ARCHIVED = "project.project.archived";
    private static final String TOPIC_TEAM_ASSIGNED    = "project.team.assigned";

    private final NotificationApplicationService notificationService;
    private final ProcessedEventJpaRepository processedEventRepository;
    private final NotificationPushPort notificationPushPort;
    private final ObjectMapper objectMapper;

    public ProjectEventConsumer(NotificationApplicationService notificationService,
            ProcessedEventJpaRepository processedEventRepository,
            NotificationPushPort notificationPushPort,
            ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.processedEventRepository = processedEventRepository;
        this.notificationPushPort = notificationPushPort;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = {TOPIC_PROJECT_CREATED, TOPIC_PROJECT_ARCHIVED, TOPIC_TEAM_ASSIGNED},
            groupId = "notification-group")
    @Transactional
    public void consume(ConsumerRecord<String, String> record) {
        Context traceContext = KafkaTracingSupport.extractTraceContext(record.headers());
        try (Scope ignored = traceContext.makeCurrent()) {
            processRecord(record.topic(), record.value());
        }
    }

    private void processRecord(String topic, String message) {
        // ── Up-front parse (M1 poison guard) ─────────────────────────────────
        JsonNode root;
        try {
            root = objectMapper.readTree(message);
        } catch (JsonProcessingException e) {
            log.error("Malformed JSON on topic {} — discarding (poison)", topic, e);
            return;
        }

        String eventId;
        String eventType;
        UUID tenantId;
        UUID projectId;
        JsonNode payload;
        try {
            eventId = requiredText(root, "eventId");
            eventType = requiredText(root, "eventType");
            tenantId = requiredUuid(root, "tenantId");
            payload = root.get("payload");
            if (payload == null || payload.isNull()) {
                throw new MalformedEventException("Missing required field: payload");
            }
            projectId = requiredUuid(payload, "projectId");
        } catch (MalformedEventException | IllegalArgumentException e) {
            log.error("Malformed project event on topic {} — discarding (poison): {}", topic, e.getMessage());
            return;
        }

        // ── Atomic idempotency claim (same transaction as dispatch) ────────────
        // Uses the actual record topic so events from different topics are tracked independently
        if (processedEventRepository.claimEvent(eventId, topic, Instant.now()) == 0) {
            log.debug("Skipping duplicate project event: eventId={}", eventId);
            return;
        }

        // ── Business dispatch ──────────────────────────────────────────────────
        try {
            dispatch(eventType, tenantId, projectId, payload);
            log.info("Processed project event: eventId={}, type={}, topic={}", eventId, eventType, topic);
        } catch (Exception e) {
            log.error("Failed to dispatch project event eventId={}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Failed to process project event", e);
        }
    }

    private void dispatch(String eventType, UUID tenantId, UUID projectId, JsonNode payload) {
        switch (eventType) {
            case "ProjectCreated" -> {
                UUID ownerId = uuidOrNull(payload, "ownerId");
                if (ownerId != null) {
                    String name = textOrDefault(payload, "name", "project");
                    Notification notification = notificationService.create(tenantId, ownerId,
                            NotificationType.PROJECT_CREATED, projectId,
                            "Project '" + name + "' was created");
                    final UUID recipientId = ownerId;
                    final NotificationResponse response = NotificationResponse.from(notification);
                    pushAfterCommit(recipientId, response);
                }
            }
            case "ProjectArchived" -> {
                UUID ownerId = uuidOrNull(payload, "ownerId");
                if (ownerId != null) {
                    String name = textOrDefault(payload, "name", "project");
                    Notification notification = notificationService.create(tenantId, ownerId,
                            NotificationType.PROJECT_ARCHIVED, projectId,
                            "Project '" + name + "' was archived");
                    final UUID recipientId = ownerId;
                    final NotificationResponse response = NotificationResponse.from(notification);
                    pushAfterCommit(recipientId, response);
                }
            }
            case "TeamAssignedToProject" -> {
                List<UUID> memberIds = parseMemberIds(payload);
                for (UUID memberId : memberIds) {
                    Notification notification = notificationService.create(tenantId, memberId,
                            NotificationType.TEAM_ASSIGNED_TO_PROJECT, projectId,
                            "You have been assigned to a project");
                    final UUID recipientId = memberId;
                    final NotificationResponse response = NotificationResponse.from(notification);
                    pushAfterCommit(recipientId, response);
                }
            }
            default -> log.warn("Unknown project event type — skipping: eventType={}", eventType);
        }
    }

    /**
     * Pushes the notification to the user after the current transaction commits.
     *
     * <p>If no transaction synchronization is active (e.g. in unit tests), the push is
     * executed immediately as a fallback. In production the consumer method is always
     * {@code @Transactional}, so the push always fires after commit.
     */
    private void pushAfterCommit(UUID recipientId, NotificationResponse response) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notificationPushPort.pushToUser(recipientId, response);
                }
            });
        } else {
            notificationPushPort.pushToUser(recipientId, response);
        }
    }

    /**
     * Parses the {@code memberIds} array into UUIDs.
     *
     * <p><strong>FIX D (poison-loop guard):</strong> a malformed member id is SKIPPED (logged at
     * WARN), never fatal. Previously an unguarded {@code UUID.fromString} threw
     * {@link IllegalArgumentException}; called from {@link #dispatch} inside the catch that rethrows
     * as {@code RuntimeException}, the Kafka offset was never committed → infinite redelivery
     * (poison loop) on a single bad member id. Skipping the bad entry keeps the valid members'
     * notifications intact while removing the infinite-retry hazard — consistent with this
     * consumer's poison-handling convention (bad data is discarded, never retried forever).
     */
    private List<UUID> parseMemberIds(JsonNode payload) {
        List<UUID> result = new ArrayList<>();
        if (payload.has("memberIds") && payload.get("memberIds").isArray()) {
            for (JsonNode node : payload.get("memberIds")) {
                String val = node.asText();
                if (val.isBlank() || val.equals("null")) {
                    continue;
                }
                try {
                    result.add(UUID.fromString(val));
                } catch (IllegalArgumentException e) {
                    log.warn("Skipping malformed member id in TeamAssignedToProject payload on topic {}: '{}'",
                            TOPIC_TEAM_ASSIGNED, val);
                }
            }
        }
        return result;
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

    private UUID uuidOrNull(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            String value = node.get(field).asText();
            if (!value.isBlank() && !value.equals("null")) {
                try {
                    return UUID.fromString(value);
                } catch (IllegalArgumentException e) {
                    log.warn("Skipping malformed UUID for field '{}': {}", field, value);
                    return null;
                }
            }
        }
        return null;
    }

    private String textOrDefault(JsonNode node, String field, String defaultValue) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return defaultValue;
    }
}
