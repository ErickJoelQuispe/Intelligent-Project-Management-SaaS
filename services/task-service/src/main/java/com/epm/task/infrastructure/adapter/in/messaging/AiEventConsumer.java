package com.epm.task.infrastructure.adapter.in.messaging;

import java.util.UUID;

import com.epm.task.domain.exception.ProjectServiceUnavailableException;
import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.port.in.CreateTaskUseCase;
import com.epm.task.domain.port.in.command.CreateTaskCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;
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
 * <p><strong>Failure classification</strong>: failures inside the per-draft loop are
 * classified as either BUSINESS or INFRASTRUCTURE:
 * <ul>
 *   <li><strong>Business/validation</strong> (domain {@code IllegalArgumentException},
 *       bad draft, unknown priority) — logged and skipped; the batch continues. This also
 *       covers NON-transient {@link org.springframework.dao.DataAccessException} subtypes such
 *       as {@link org.springframework.dao.DataIntegrityViolationException}: a draft value that
 *       violates a DB CHECK/length/not-null constraint is a data problem with THAT draft, not an
 *       outage, so it is skipped rather than rethrown — rethrowing would spin an infinite Kafka
 *       poison loop (re-classified as infra on every redelivery, re-creating earlier drafts).</li>
 *   <li><strong>Infrastructure (transient only)</strong>
 *       ({@link ProjectServiceUnavailableException}, {@link CallNotPermittedException}, and
 *       {@link TransientDataAccessException} — which covers query timeouts and lock-acquisition
 *       failures) — these are genuinely transient and affect the whole batch, not just one
 *       draft. The idempotency claim is released (compensated) via
 *       {@link ProcessedEventClaimer#releaseClaim} in its own {@code REQUIRES_NEW} transaction,
 *       then the exception is rethrown to trigger Kafka retry → DLT.</li>
 * </ul>
 *
 * <p><strong>Why release the claim on infrastructure failure?</strong> The claim is
 * committed in {@code REQUIRES_NEW} before the loop. If an infrastructure failure causes a
 * rethrow without releasing the claim, Kafka redelivery would see the event as already
 * claimed and silently skip it — the AI batch would be permanently lost. Releasing the
 * claim allows genuine retries while the claim still guards against true concurrent duplicates.
 *
 * <p><strong>AT-LEAST-ONCE delivery trade-off (accepted, documented):</strong> processing
 * AI-generated draft batches is effectively <em>at-least-once</em>. Each draft is committed
 * in its own transaction; the idempotency claim is committed separately (via
 * {@code REQUIRES_NEW}) before the per-draft loop. If an infrastructure failure occurs
 * mid-batch (e.g. on draft k+1 out of N), the consumer releases the claim and rethrows
 * to enable Kafka redelivery — but the k tasks already committed in earlier iterations are
 * NOT rolled back. On redelivery the whole batch is replayed from scratch, so those k tasks
 * are created again, resulting in duplicates.
 *
 * <p>This is an accepted trade-off for the following reasons:
 * <ol>
 *   <li>AI drafts are human-curated before they become actionable. Duplicate drafts surface
 *       as review noise rather than silent data corruption; a human can identify and discard
 *       them.</li>
 *   <li>The alternative — per-draft idempotency keyed on {@code (eventId, draftIndex)} —
 *       requires additional schema (a compound idempotency key per draft) and more complex
 *       claim logic. This is deferred as a future improvement.</li>
 * </ol>
 *
 * <p><strong>Path to exactly-once:</strong> implement per-draft idempotency using a
 * composite key {@code (eventId, draftIndex)} in the processed-events table, checked and
 * claimed atomically inside each draft's own {@code CreateTaskUseCase.execute} transaction.
 * Until then, callers and operators should be aware that duplicate drafts may appear in the
 * database after infrastructure failures mid-batch.
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
    private final ProcessedEventClaimer processedEventClaimer;
    private final ObjectMapper objectMapper;

    public AiEventConsumer(CreateTaskUseCase createTaskUseCase,
            IdempotencyGuard idempotencyGuard,
            ProcessedEventClaimer processedEventClaimer,
            ObjectMapper objectMapper) {
        this.createTaskUseCase = createTaskUseCase;
        this.idempotencyGuard = idempotencyGuard;
        this.processedEventClaimer = processedEventClaimer;
        this.objectMapper = objectMapper;
    }

    /**
     * Consumes an {@code AiTasksGenerated} event.
     *
     * <p>NOT {@code @Transactional} — each task creation is transactional on its own.
     * This prevents holding a DB connection open across the synchronous Feign membership
     * check inside {@link CreateTaskUseCase#execute}.
     *
     * <p>Infrastructure failures (DB down, circuit breaker open) release the idempotency
     * claim before rethrowing, enabling Kafka redelivery to retry the full batch.
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

        // ── Per-draft creation — best-effort for business errors, fail-fast for infra ─
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

            } catch (ProjectServiceUnavailableException | CallNotPermittedException
                    | TransientDataAccessException infraEx) {
                // ── Infrastructure failure (TRANSIENT only): affects the whole batch ─
                // Only genuinely transient access failures are treated as infrastructure:
                // project-service unavailable, circuit-breaker open, and TransientDataAccessException
                // (which covers QueryTimeoutException, CannotAcquireLockException, and other
                // ConcurrencyFailureException subtypes — a retry may succeed). NON-transient
                // DataAccessException subtypes (e.g. DataIntegrityViolationException from a DB
                // constraint violation) are NOT caught here — they fall through to the per-draft
                // business catch below, because retrying them would just poison-loop the partition.
                //
                // Release the idempotency claim so Kafka redelivery can retry this batch.
                // Without this compensation, the redelivered event would be silently skipped
                // (the claim was already committed), permanently losing the AI batch.
                log.error("AiTasksGenerated eventId={}: infrastructure failure mid-batch"
                        + " — releasing claim and rethrowing for retry: {}",
                        eventId, infraEx.getMessage());
                try {
                    processedEventClaimer.releaseClaim(eventId);
                } catch (Exception releaseEx) {
                    log.error("AiTasksGenerated eventId={}: failed to release idempotency claim: {}",
                            eventId, releaseEx.getMessage());
                }
                throw new RuntimeException("Infrastructure failure processing AiTasksGenerated event " + eventId
                        + " — redelivery required", infraEx);

            } catch (Exception e) {
                // ── Business/validation failure: per-draft, skip and continue ─
                // Includes NON-transient DataAccessException subtypes such as
                // DataIntegrityViolationException: a draft value that violates a DB constraint
                // is a data problem with THIS draft, not an infrastructure outage. Skipping it
                // (rather than rethrowing) prevents an infinite Kafka poison loop.
                log.warn("AiTasksGenerated eventId={}: failed to create a task"
                        + " (business/validation error) — skipping draft: {}",
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
