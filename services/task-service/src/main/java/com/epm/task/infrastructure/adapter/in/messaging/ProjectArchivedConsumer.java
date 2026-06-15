package com.epm.task.infrastructure.adapter.in.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.epm.task.domain.event.ProjectTasksCancelled;
import com.epm.task.domain.port.out.DomainEventPublisher;
import com.epm.task.domain.port.out.TaskRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.uuid.UuidCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer for {@code project.project.archived} events.
 *
 * <p>Bulk-cancels all tasks in the archived project via a single UPDATE (no N+1).
 * Uses the two-bean idempotency pattern ({@link IdempotencyGuard} +
 * {@link ProcessedEventClaimer}) to prevent TOCTOU races when the same event is
 * delivered concurrently.
 *
 * <p><strong>Cascade semantics</strong>: instead of emitting one {@code TaskStatusChanged}
 * event per task (which could be thousands), a single aggregate
 * {@link ProjectTasksCancelled} event is emitted after the bulk cancel, inserted into
 * the outbox in the SAME transaction. Downstream consumers (Kanban) refresh on schedule
 * and do not require per-task events.
 *
 * <p><strong>Transaction boundary</strong>: the idempotency claim runs in its own
 * {@code REQUIRES_NEW} transaction (via {@link IdempotencyGuard}). The business logic
 * (bulk cancel + aggregate event outbox insert) runs in this bean's {@code @Transactional}
 * boundary — these are separate so that the claim commit is visible to concurrent consumers
 * before the business work begins.
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
    private final DomainEventPublisher eventPublisher;
    private final IdempotencyGuard idempotencyGuard;
    private final ObjectMapper objectMapper;

    public ProjectArchivedConsumer(TaskRepository taskRepository,
            DomainEventPublisher eventPublisher,
            IdempotencyGuard idempotencyGuard,
            ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
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

        // ── Business logic: bulk cancel + single aggregate outbox event ────────
        // Single bulk UPDATE — no N+1. Does not emit per-task events; a single
        // ProjectTasksCancelled aggregate event is emitted into the outbox instead.
        int cancelledCount = taskRepository.bulkCancelByProjectId(projectId, tenantId);

        ProjectTasksCancelled aggregateEvent = new ProjectTasksCancelled(
                UuidCreator.getTimeOrderedEpoch(),
                projectId,
                tenantId,
                cancelledCount,
                Instant.now());
        eventPublisher.publish(List.of(aggregateEvent));

        log.info("Processed ProjectArchived for projectId={}, {} tasks bulk-cancelled",
                projectId, cancelledCount);
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
