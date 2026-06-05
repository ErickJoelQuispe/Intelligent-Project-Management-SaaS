package com.epm.notification.infrastructure.adapter.in.messaging;

import java.time.Instant;
import java.util.UUID;

import com.epm.notification.application.usecase.NotificationApplicationService;
import com.epm.notification.domain.model.Notification;
import com.epm.notification.domain.model.NotificationType;
import com.epm.notification.domain.port.out.NotificationPushPort;
import com.epm.notification.infrastructure.adapter.in.web.NotificationResponse;
import com.epm.notification.infrastructure.adapter.out.persistence.ProcessedEventJpaEntity;
import com.epm.notification.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import com.epm.notification.infrastructure.messaging.tracing.KafkaTracingSupport;
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
 * Kafka consumer for {@code task.events} topic.
 *
 * <p>Dispatches by {@code eventType} field in the JSON envelope and creates
 * in-app notifications for relevant task domain events. Uses {@code processed_events}
 * table for idempotency — duplicate events are skipped.
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
    private final ProcessedEventJpaRepository processedEventRepo;
    private final NotificationPushPort notificationPushPort;
    private final ObjectMapper objectMapper;

    public TaskEventConsumer(NotificationApplicationService notificationService,
            ProcessedEventJpaRepository processedEventRepo,
            NotificationPushPort notificationPushPort,
            ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.processedEventRepo = processedEventRepo;
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
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventId = root.get("eventId").asText();

            // Idempotency check
            if (processedEventRepo.existsByEventId(eventId)) {
                log.warn("Skipping duplicate task event: eventId={}", eventId);
                return;
            }

            String eventType = root.get("eventType").asText();
            UUID tenantId = UUID.fromString(root.get("tenantId").asText());
            JsonNode payload = root.get("payload");
            UUID taskId = UUID.fromString(payload.get("taskId").asText());

            dispatch(eventType, tenantId, taskId, payload);

            processedEventRepo.save(new ProcessedEventJpaEntity(eventId, TOPIC, Instant.now()));
            log.info("Processed task event: eventId={}, type={}", eventId, eventType);

        } catch (Exception e) {
            log.error("Failed to process task event: {}", e.getMessage(), e);
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
                    notificationPushPort.pushToUser(assigneeId, NotificationResponse.from(notification));
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
                    notificationPushPort.pushToUser(assigneeId, NotificationResponse.from(notification));
                }
            }
            case "TaskDeleted" -> {
                UUID assigneeId = uuidOrNull(payload, "assigneeId");
                if (assigneeId != null) {
                    Notification notification = notificationService.create(tenantId, assigneeId,
                            NotificationType.TASK_DELETED, taskId,
                            "A task you were assigned to was deleted");
                    notificationPushPort.pushToUser(assigneeId, NotificationResponse.from(notification));
                }
            }
            case "TaskCreated" -> {
                UUID actorId = uuidOrNull(payload, "actorId");
                if (actorId != null) {
                    Notification notification = notificationService.create(tenantId, actorId,
                            NotificationType.TASK_CREATED, taskId,
                            "Task '" + titleOrDefault(payload) + "' was created");
                    notificationPushPort.pushToUser(actorId, NotificationResponse.from(notification));
                }
            }
            default -> log.debug("Ignoring unknown task event type: {}", eventType);
        }
    }

    private UUID uuidOrNull(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            String value = node.get(field).asText();
            if (!value.isBlank() && !value.equals("null")) {
                return UUID.fromString(value);
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
