package com.epm.notification.infrastructure.adapter.in.messaging;

import java.time.Instant;
import java.util.UUID;

import com.epm.notification.application.usecase.NotificationApplicationService;
import com.epm.notification.domain.model.NotificationType;
import com.epm.notification.infrastructure.adapter.out.persistence.ProcessedEventJpaEntity;
import com.epm.notification.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 */
@Component
public class TaskEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskEventConsumer.class);
    private static final String TOPIC = "task.events";

    private final NotificationApplicationService notificationService;
    private final ProcessedEventJpaRepository processedEventRepo;
    private final ObjectMapper objectMapper;

    public TaskEventConsumer(NotificationApplicationService notificationService,
            ProcessedEventJpaRepository processedEventRepo,
            ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.processedEventRepo = processedEventRepo;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = TOPIC, groupId = "notification-group")
    @Transactional
    public void consume(String message) {
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
                    notificationService.create(tenantId, assigneeId,
                            NotificationType.TASK_ASSIGNED, taskId,
                            "Task was assigned to you");
                }
            }
            case "TaskStatusChanged" -> {
                UUID assigneeId = uuidOrNull(payload, "assigneeId");
                if (assigneeId != null) {
                    String newStatus = payload.has("newStatus")
                            ? payload.get("newStatus").asText() : "unknown";
                    notificationService.create(tenantId, assigneeId,
                            NotificationType.TASK_STATUS_CHANGED, taskId,
                            "Task status changed to " + newStatus);
                }
            }
            case "TaskDeleted" -> {
                UUID assigneeId = uuidOrNull(payload, "assigneeId");
                if (assigneeId != null) {
                    notificationService.create(tenantId, assigneeId,
                            NotificationType.TASK_DELETED, taskId,
                            "A task you were assigned to was deleted");
                }
            }
            case "TaskCreated" -> {
                // TaskCreated: store project-level notification using actorId as creator
                UUID actorId = uuidOrNull(payload, "actorId");
                if (actorId != null) {
                    notificationService.create(tenantId, actorId,
                            NotificationType.TASK_CREATED, taskId,
                            "Task '" + titleOrDefault(payload) + "' was created");
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
