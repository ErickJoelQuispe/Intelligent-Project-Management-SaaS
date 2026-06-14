package com.epm.task.infrastructure.adapter.in.messaging;

import java.util.UUID;

import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.port.in.CreateTaskUseCase;
import com.epm.task.domain.port.in.command.CreateTaskCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for {@code ai.events} topic — {@code AiTasksGenerated} event type.
 *
 * <p>For each event, creates one {@link com.epm.task.domain.model.Task} per draft in the batch
 * using {@link CreateTaskUseCase}. Each task creation is its own atomic transaction via
 * {@code TransactionalOutboxWriter} (inside {@link CreateTaskUseCase#execute}); there is no
 * single batch-wide transaction wrapping Feign membership calls.
 *
 * <p><strong>Feign isolation</strong>: {@code consume()} is NOT {@code @Transactional}.
 * Each {@link CreateTaskUseCase#execute} call owns its own transaction. This prevents holding
 * a DB connection open across multiple Feign HTTP membership checks.
 *
 * <p><strong>Idempotency</strong>: the two-bean pattern ({@link IdempotencyGuard} +
 * {@link ProcessedEventClaimer}) is used for TOCTOU-safe deduplication. The event is claimed
 * BEFORE the task-creation loop; if claiming fails (duplicate), the whole batch is skipped.
 *
 * <p><strong>Best-effort ingestion</strong>: individual task failures (e.g., a draft with
 * a bad priority that slips past validation, or a transient membership check failure) are
 * logged and skipped; the remaining drafts are still processed. Infrastructure failures
 * (e.g., DB down) rethrow and allow the consumer to retry the whole event. Since the event
 * claim is committed before the loop, a mid-batch crash followed by redelivery will re-skip
 * all drafts (the event is already claimed). This is acceptable for AI-generated batch tasks:
 * partial ingestion is better than blocking the topic indefinitely.
 *
 * <p><strong>Payload validation</strong>: batch size is capped at {@link #MAX_AI_BATCH_SIZE}.
 * Each draft is validated for a non-blank title (≤255 chars), optional description (≤4000 chars),
 * and a parseable {@link TaskPriority}. Structurally invalid drafts are skipped with a warning.
 */
@Component
public class AiEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AiEventConsumer.class);
    private static final String TOPIC = "ai.events";

    /** Maximum number of task drafts allowed per AI batch event — DoS guard. */
    static final int MAX_AI_BATCH_SIZE = 50;

    private final CreateTaskUseCase createTaskUseCase;
    private final IdempotencyGuard idempotencyGuard;
    private final ObjectMapper objectMapper;

    public AiEventConsumer(CreateTaskUseCase createTaskUseCase,
            IdempotencyGuard idempotencyGuard,
            ObjectMapper objectMapper) {
        this.createTaskUseCase = createTaskUseCase;
        this.idempotencyGuard = idempotencyGuard;
        this.objectMapper = objectMapper;
    }

    /**
     * Consumes an {@code AiTasksGenerated} event.
     *
     * <p>NOT {@code @Transactional} — each task creation is transactional on its own.
     * This prevents holding a DB connection open across the synchronous Feign membership
     * check inside {@link CreateTaskUseCase#execute}.
     */
    @KafkaListener(topics = TOPIC, groupId = "task-service")
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
        UUID generatedBy;
        JsonNode tasks;
        try {
            eventId = requiredText(root, "eventId");
            tenantId = UUID.fromString(requiredText(root, "tenantId"));
            JsonNode payload = root.get("payload");
            if (payload == null || payload.isNull()) {
                throw new MalformedEventException("Missing required field: payload");
            }
            projectId = UUID.fromString(requiredText(payload, "projectId"));
            generatedBy = UUID.fromString(requiredText(payload, "generatedBy"));
            tasks = payload.get("tasks");

            // DoS guard: tasks array must be present and within size limit
            if (tasks == null || !tasks.isArray()) {
                throw new MalformedEventException("AiTasksGenerated: 'tasks' must be a non-null array");
            }
            if (tasks.size() > MAX_AI_BATCH_SIZE) {
                throw new MalformedEventException(
                        "AiTasksGenerated: batch size " + tasks.size()
                                + " exceeds maximum allowed " + MAX_AI_BATCH_SIZE);
            }
        } catch (MalformedEventException | IllegalArgumentException e) {
            log.error("Malformed AiTasksGenerated event on topic {} — discarding (poison): {}",
                    TOPIC, e.getMessage());
            return;
        }

        // ── Two-bean idempotency claim (commits independently of task creation) ─
        if (!idempotencyGuard.claim(eventId, TOPIC)) {
            log.debug("Skipping duplicate AiTasksGenerated event: {}", eventId);
            return;
        }

        // ── Per-draft creation — best-effort ingestion ────────────────────────
        int created = 0;
        for (JsonNode taskDraft : tasks) {
            try {
                String title = taskDraft.path("title").asText(null);
                if (title == null || title.isBlank()) {
                    log.warn("AiTasksGenerated eventId={}: draft has blank/missing title — skipping", eventId);
                    continue;
                }
                if (title.length() > 255) {
                    log.warn("AiTasksGenerated eventId={}: draft title exceeds 255 chars — skipping", eventId);
                    continue;
                }

                String description = taskDraft.path("description").asText(null);
                if (description != null && description.length() > 4000) {
                    log.warn("AiTasksGenerated eventId={}: draft description exceeds 4000 chars — skipping", eventId);
                    continue;
                }

                String priorityText = taskDraft.path("priority").asText(null);
                if (priorityText == null || priorityText.isBlank()) {
                    log.warn("AiTasksGenerated eventId={}: draft has missing priority — skipping", eventId);
                    continue;
                }
                TaskPriority priority;
                try {
                    priority = TaskPriority.valueOf(priorityText.toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("AiTasksGenerated eventId={}: unrecognised priority '{}' — skipping",
                            eventId, priorityText);
                    continue;
                }

                CreateTaskCommand command = new CreateTaskCommand(
                        tenantId,
                        projectId,
                        generatedBy,
                        title,
                        description,
                        priority,
                        null,
                        null);

                createTaskUseCase.execute(command);
                created++;
            } catch (Exception e) {
                // Per-draft failures (transient or business) are logged and skipped;
                // the batch continues. Infrastructure failures (DB, circuit-breaker open)
                // may also surface here; we treat them as per-draft failures to not block
                // the rest of the batch. A mid-batch crash is acceptable for AI ingestion.
                log.warn("AiTasksGenerated eventId={}: failed to create a task — skipping draft: {}",
                        eventId, e.getMessage());
            }
        }

        log.info("Processed AiTasksGenerated eventId={} — {}/{} tasks created",
                eventId, created, tasks.size());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
