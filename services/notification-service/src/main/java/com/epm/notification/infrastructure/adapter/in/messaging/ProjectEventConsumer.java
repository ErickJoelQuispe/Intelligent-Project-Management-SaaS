package com.epm.notification.infrastructure.adapter.in.messaging;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
 * Kafka consumer for {@code project.events} topic.
 *
 * <p>Dispatches by {@code eventType} field in the JSON envelope and creates
 * in-app notifications for relevant project domain events. Uses {@code processed_events}
 * table for idempotency — duplicate events are skipped.
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
    private static final String TOPIC = "project.events";

    private final NotificationApplicationService notificationService;
    private final ProcessedEventJpaRepository processedEventRepo;
    private final ObjectMapper objectMapper;

    public ProjectEventConsumer(NotificationApplicationService notificationService,
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
                log.warn("Skipping duplicate project event: eventId={}", eventId);
                return;
            }

            String eventType = root.get("eventType").asText();
            UUID tenantId = UUID.fromString(root.get("tenantId").asText());
            JsonNode payload = root.get("payload");
            UUID projectId = UUID.fromString(payload.get("projectId").asText());

            dispatch(eventType, tenantId, projectId, payload);

            processedEventRepo.save(new ProcessedEventJpaEntity(eventId, TOPIC, Instant.now()));
            log.info("Processed project event: eventId={}, type={}", eventId, eventType);

        } catch (Exception e) {
            log.error("Failed to process project event: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process project event", e);
        }
    }

    private void dispatch(String eventType, UUID tenantId, UUID projectId, JsonNode payload) {
        switch (eventType) {
            case "ProjectCreated" -> {
                UUID ownerId = uuidOrNull(payload, "ownerId");
                if (ownerId != null) {
                    String name = textOrDefault(payload, "name", "project");
                    notificationService.create(tenantId, ownerId,
                            NotificationType.PROJECT_CREATED, projectId,
                            "Project '" + name + "' was created");
                }
            }
            case "ProjectArchived" -> {
                UUID ownerId = uuidOrNull(payload, "ownerId");
                if (ownerId != null) {
                    String name = textOrDefault(payload, "name", "project");
                    notificationService.create(tenantId, ownerId,
                            NotificationType.PROJECT_ARCHIVED, projectId,
                            "Project '" + name + "' was archived");
                }
            }
            case "TeamAssignedToProject" -> {
                List<UUID> memberIds = parseMemberIds(payload);
                for (UUID memberId : memberIds) {
                    notificationService.create(tenantId, memberId,
                            NotificationType.TEAM_ASSIGNED_TO_PROJECT, projectId,
                            "You have been assigned to a project");
                }
            }
            default -> log.warn("Unknown project event type — skipping: eventType={}", eventType);
        }
    }

    private List<UUID> parseMemberIds(JsonNode payload) {
        List<UUID> result = new ArrayList<>();
        if (payload.has("memberIds") && payload.get("memberIds").isArray()) {
            for (JsonNode node : payload.get("memberIds")) {
                String val = node.asText();
                if (!val.isBlank() && !val.equals("null")) {
                    result.add(UUID.fromString(val));
                }
            }
        }
        return result;
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

    private String textOrDefault(JsonNode node, String field, String defaultValue) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return defaultValue;
    }
}
