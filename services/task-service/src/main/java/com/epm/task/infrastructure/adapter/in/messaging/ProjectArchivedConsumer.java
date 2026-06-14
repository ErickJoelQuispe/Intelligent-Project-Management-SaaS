package com.epm.task.infrastructure.adapter.in.messaging;

import java.util.List;
import java.util.UUID;

import com.epm.task.domain.model.Task;
import com.epm.task.domain.port.out.ActivityLogRepository;
import com.epm.task.domain.port.out.DomainEventPublisher;
import com.epm.task.domain.port.out.TaskRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer for {@code project.project.archived} events.
 *
 * <p>Cancels all tasks in the archived project. Uses the two-bean idempotency pattern
 * ({@link IdempotencyGuard} + {@link ProcessedEventClaimer}) to prevent TOCTOU races
 * when the same event is delivered concurrently.
 *
 * <p>Parsing is up-front and typed — missing required fields throw
 * {@link MalformedEventException} which is caught and logged (poison-skip) without
 * triggering a retry. Business or infrastructure failures rethrow and allow Kafka to retry.
 */
@Component
public class ProjectArchivedConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProjectArchivedConsumer.class);
    private static final String TOPIC = "project.project.archived";

    private final TaskRepository taskRepository;
    private final ActivityLogRepository activityLogRepository;
    private final DomainEventPublisher eventPublisher;
    private final IdempotencyGuard idempotencyGuard;
    private final ObjectMapper objectMapper;

    public ProjectArchivedConsumer(TaskRepository taskRepository,
            ActivityLogRepository activityLogRepository,
            DomainEventPublisher eventPublisher,
            IdempotencyGuard idempotencyGuard,
            ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.activityLogRepository = activityLogRepository;
        this.eventPublisher = eventPublisher;
        this.idempotencyGuard = idempotencyGuard;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = TOPIC, groupId = "task-service-project-archived")
    @Transactional
    public void consume(String message) {
        // ── Up-front parse ────────────────────────────────────────────────────
        JsonNode root;
        try {
            root = objectMapper.readTree(message);
        } catch (JsonProcessingException e) {
            log.error("Malformed JSON on topic {} — discarding (poison)", TOPIC, e);
            return;
        }

        String eventId;
        UUID projectId;
        UUID tenantId;
        try {
            eventId = requiredText(root, "eventId");
            tenantId = UUID.fromString(requiredText(root, "tenantId"));
            JsonNode payload = root.get("payload");
            if (payload == null || payload.isNull()) {
                throw new MalformedEventException("Missing required field: payload");
            }
            projectId = UUID.fromString(requiredText(payload, "projectId"));
        } catch (MalformedEventException | IllegalArgumentException e) {
            log.error("Malformed ProjectArchived event on topic {} — discarding (poison): {}",
                    TOPIC, e.getMessage());
            return;
        }

        // ── Two-bean idempotency claim ─────────────────────────────────────────
        if (!idempotencyGuard.claim(eventId, TOPIC)) {
            log.debug("Skipping duplicate ProjectArchived event: {}", eventId);
            return;
        }

        // ── Business logic ────────────────────────────────────────────────────
        List<Task> tasks = taskRepository.findAllByProjectId(projectId, tenantId);
        for (Task task : tasks) {
            if (task.getStatus() != com.epm.task.domain.model.TaskStatus.CANCELLED) {
                task.cancel();
                taskRepository.save(task);
                eventPublisher.publish(task.pullDomainEvents());
            }
        }

        log.info("Processed ProjectArchived for projectId={}, {} tasks cancelled",
                projectId, tasks.size());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText(null) == null) {
            throw new MalformedEventException("Missing required field: " + field);
        }
        String text = value.asText(null);
        if (text == null || text.isBlank()) {
            throw new MalformedEventException("Empty required field: " + field);
        }
        return text;
    }
}
