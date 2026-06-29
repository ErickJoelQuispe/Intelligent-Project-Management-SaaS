package com.epm.notification.infrastructure.adapter.in.messaging;

import java.time.Instant;
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
 * Kafka consumer for {@code task.events} topic.
 *
 * <p>Dispatches by {@code eventType} field in the JSON envelope and creates
 * in-app notifications for relevant task domain events.
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
 * <p>After persisting each notification, pushes it via {@link NotificationPushPort}
 * (STOMP/WebSocket) to connected clients. Fire-and-forget — if user is disconnected,
 * the push is silently dropped.
 */
@Component
public class TaskEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskEventConsumer.class);
    private static final String TOPIC = "task.events";

    private final NotificationApplicationService notificationService;
    private final ProcessedEventJpaRepository processedEventRepository;
    private final NotificationPushPort notificationPushPort;
    private final ObjectMapper objectMapper;

    public TaskEventConsumer(NotificationApplicationService notificationService,
            ProcessedEventJpaRepository processedEventRepository,
            NotificationPushPort notificationPushPort,
            ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.processedEventRepository = processedEventRepository;
        this.notificationPushPort = notificationPushPort;
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
        UUID taskId;
        JsonNode payload;
        try {
            eventId = requiredText(root, "eventId");
            eventType = requiredText(root, "eventType");
            tenantId = requiredUuid(root, "tenantId");
            payload = root.get("payload");
            if (payload == null || payload.isNull()) {
                throw new MalformedEventException("Missing required field: payload");
            }
            taskId = requiredUuid(payload, "taskId");
        } catch (MalformedEventException | IllegalArgumentException e) {
            log.error("Malformed task event on topic {} — discarding (poison): {}", TOPIC, e.getMessage());
            return;
        }

        // ── Atomic idempotency claim (same transaction as dispatch) ────────────
        if (processedEventRepository.claimEvent(eventId, TOPIC, Instant.now()) == 0) {
            log.debug("Skipping duplicate task event: eventId={}", eventId);
            return;
        }

        // ── Business dispatch ──────────────────────────────────────────────────
        try {
            dispatch(eventType, tenantId, taskId, payload);
            log.info("Processed task event: eventId={}, type={}", eventId, eventType);
        } catch (Exception e) {
            log.error("Failed to dispatch task event eventId={}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Failed to process task event", e);
        }
    }

    private void dispatch(String eventType, UUID tenantId, UUID taskId, JsonNode payload) {
        switch (eventType) {
            case "TaskAssigned" -> {
                UUID assigneeId = uuidOrNull(payload, "assigneeId");
                if (assigneeId != null) {
                    Notification notification = notificationService.create(tenantId, assigneeId,
                            NotificationType.TASK_ASSIGNED, taskId,
                            "Task was assigned to you");
                    pushAfterCommit(assigneeId, NotificationResponse.from(notification));
                }
            }
            case "TaskStatusChanged" -> {
                UUID assigneeId = uuidOrNull(payload, "assigneeId");
                if (assigneeId != null) {
                    String newStatus = payload.has("newStatus")
                            ? payload.get("newStatus").asText() : "unknown";
                    Notification notification = notificationService.create(tenantId, assigneeId,
                            NotificationType.TASK_STATUS_CHANGED, taskId,
                            "Task status changed to " + newStatus);
                    pushAfterCommit(assigneeId, NotificationResponse.from(notification));
                }
            }
            case "TaskDeleted" -> {
                UUID assigneeId = uuidOrNull(payload, "assigneeId");
                if (assigneeId != null) {
                    Notification notification = notificationService.create(tenantId, assigneeId,
                            NotificationType.TASK_DELETED, taskId,
                            "A task you were assigned to was deleted");
                    pushAfterCommit(assigneeId, NotificationResponse.from(notification));
                }
            }
            case "TaskCreated" -> {
                UUID actorId = uuidOrNull(payload, "actorId");
                if (actorId != null) {
                    Notification notification = notificationService.create(tenantId, actorId,
                            NotificationType.TASK_CREATED, taskId,
                            "Task '" + titleOrDefault(payload) + "' was created");
                    pushAfterCommit(actorId, NotificationResponse.from(notification));
                }
            }
            default -> log.warn("Ignoring unknown task event type: {}", eventType);
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

    private String titleOrDefault(JsonNode payload) {
        if (payload.has("title") && !payload.get("title").isNull()) {
            return payload.get("title").asText();
        }
        return "task";
    }
}
