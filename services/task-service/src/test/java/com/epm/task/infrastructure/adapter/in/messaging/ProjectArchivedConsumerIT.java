package com.epm.task.infrastructure.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.model.TaskStatus;
import com.epm.task.infrastructure.AbstractPostgresIT;
import com.epm.task.infrastructure.adapter.out.persistence.ActivityLogJpaRepository;
import com.epm.task.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import com.epm.task.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import com.epm.task.infrastructure.adapter.out.persistence.TaskJpaEntity;
import com.epm.task.infrastructure.adapter.out.persistence.TaskJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Concurrent idempotency IT for {@link ProjectArchivedConsumer}.
 *
 * <p>Two threads invoke {@link ProjectArchivedConsumer#consume(String)} DIRECTLY with the
 * SAME eventId, released together via a start-gate. We call the injected Spring bean
 * (not {@code this}) so the {@code @Transactional} proxy opens an independent transaction
 * per call — which is exactly the concurrent flow that exercises the processed_events PK backstop.
 *
 * <p>Asserts:
 * <ol>
 *   <li>NEITHER task threw — with the {@code REQUIRES_NEW} {@link IdempotencyGuard} the
 *       race loser skips benignly and the consumer transaction is never poisoned.</li>
 *   <li>Exactly ONE {@code processed_events} row exists for the eventId.</li>
 *   <li>The bulk cancel happened exactly once (non-cancelled tasks end up CANCELLED,
 *       but only the count from a single run).</li>
 * </ol>
 *
 * <p>Requires Docker (Testcontainers). If Docker is unavailable the test will fail to
 * start — do NOT weaken it to make it pass.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "eureka.client.enabled=false",
    "spring.kafka.bootstrap-servers=localhost:9092",
    "spring.task.scheduling.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.jwks-uri=https://example.com/.well-known/jwks.json"
})
class ProjectArchivedConsumerIT extends AbstractPostgresIT {

    @Autowired
    private ProjectArchivedConsumer consumer;

    @Autowired
    private TaskJpaRepository taskRepo;

    @Autowired
    private ActivityLogJpaRepository activityLogRepo;

    @Autowired
    private OutboxEventJpaRepository outboxRepo;

    @Autowired
    private ProcessedEventJpaRepository processedEventRepo;

    @Autowired
    private TransactionTemplate txTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID tenantId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();

        // Persist 3 TODO tasks for the project
        for (int i = 0; i < 3; i++) {
            TaskJpaEntity task = new TaskJpaEntity();
            task.setId(UUID.randomUUID());
            task.setTenantId(tenantId);
            task.setProjectId(projectId);
            task.setTitle("Task " + i);
            task.setStatus(TaskStatus.TODO);
            task.setPriority(TaskPriority.MEDIUM);
            task.setCreatedAt(Instant.now());
            task.setUpdatedAt(Instant.now());
            taskRepo.save(task);
        }
    }

    @AfterEach
    void cleanup() {
        // Delete in FK-safe order: activity_log references tasks
        outboxRepo.deleteAll();
        processedEventRepo.deleteAll();
        activityLogRepo.deleteAll();
        taskRepo.deleteAll();
    }

    /**
     * Regression guard for the REQUIRES_NEW idempotency-guard fix.
     *
     * <p>Two threads race on the SAME eventId. The {@link IdempotencyGuard} claims the
     * processed_events row in its OWN {@code REQUIRES_NEW} transaction, so the loser's
     * {@code DataIntegrityViolationException} is confined to that inner transaction and the
     * consumer's transaction is never marked rollback-only. Therefore NEITHER thread throws,
     * exactly one processed_events row exists, and the bulk cancel ran exactly once.
     */
    @Test
    void consume_concurrentSameEventId_exactlyOneProcessedRow_noExceptionEscapes() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String message = buildProjectArchivedMessage(eventId, projectId, tenantId);

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<?>> futures = new ArrayList<>();
        AtomicInteger threwCount = new AtomicInteger(0);

        try {
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    try {
                        startGate.await();
                        consumer.consume(message);
                    } catch (Exception e) {
                        threwCount.incrementAndGet();
                    }
                    return null;
                }));
            }

            startGate.countDown();
            for (Future<?> f : futures) {
                // Allow up to 120s: in a worst-case race both threads pass the idempotency
                // guard simultaneously, and thread B must wait for thread A's bulk UPDATE lock
                // to release before it can proceed and then also get caught by the idempotency
                // guard's PK backstop (since thread A's REQUIRES_NEW commit is visible).
                f.get(120, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        // NEITHER task threw
        assertThat(threwCount.get())
                .as("No concurrent task may throw — REQUIRES_NEW guard prevents rollback poisoning")
                .isZero();

        // Exactly ONE processed_events row for the eventId
        long processedCount = processedEventRepo.findAll().stream()
                .filter(p -> eventId.equals(p.getEventId()))
                .count();
        assertThat(processedCount)
                .as("Expected exactly 1 processed_events row for eventId %s", eventId)
                .isEqualTo(1);

        // All tasks in the project should be CANCELLED (bulk cancel ran once)
        long cancelledCount = taskRepo.findAll().stream()
                .filter(t -> t.getTenantId().equals(tenantId) && t.getProjectId().equals(projectId))
                .filter(t -> t.getStatus() == TaskStatus.CANCELLED)
                .count();
        assertThat(cancelledCount)
                .as("All 3 tasks must be CANCELLED after bulk cancel")
                .isEqualTo(3);

        // A single aggregate outbox event was emitted
        long outboxCount = outboxRepo.findAll().stream()
                .filter(o -> "ProjectTasksCancelled".equals(o.getEventType()))
                .count();
        assertThat(outboxCount)
                .as("Exactly 1 ProjectTasksCancelled aggregate outbox event expected")
                .isEqualTo(1);
    }

    /**
     * Regression guard for the optimistic-lock lost-update fix on {@code bulkCancelByProjectId}.
     *
     * <p>The native bulk-cancel UPDATE must increment the {@code version} column so a concurrent
     * writer holding the pre-cancel version correctly fails optimistic locking (→ HTTP 409)
     * rather than silently overwriting the CANCELLED status (lost update).
     *
     * <p>Proves two things:
     * <ol>
     *   <li>The DB {@code version} column is bumped from N to N+1 by the bulk UPDATE.</li>
     *   <li>A writer holding the stale (pre-cancel) version fails with
     *       {@link ObjectOptimisticLockingFailureException} on save.</li>
     * </ol>
     */
    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NEVER)
    void bulkCancel_incrementsVersion_staleWriterFailsOptimisticLock() {
        // Pick one TODO task and capture its pre-cancel version (N).
        UUID taskId = taskRepo.findAll().stream()
                .filter(t -> t.getProjectId().equals(projectId) && t.getStatus() == TaskStatus.TODO)
                .map(TaskJpaEntity::getId)
                .findFirst()
                .orElseThrow();
        long versionBefore = taskRepo.findById(taskId).orElseThrow().getVersion();

        // Run the bulk cancel in its own committed transaction.
        txTemplate.executeWithoutResult(status ->
                taskRepo.bulkCancelByProjectId(projectId, tenantId, Instant.now()));

        // (1) DB version column must be N+1 after the bulk UPDATE.
        long versionAfter = txTemplate.execute(status ->
                ((Number) entityManager
                        .createNativeQuery("SELECT version FROM tasks WHERE id = :id")
                        .setParameter("id", taskId)
                        .getSingleResult()).longValue());
        assertThat(versionAfter)
                .as("bulkCancelByProjectId must increment the version column (N -> N+1) "
                        + "so concurrent optimistic-locked writers fail with a conflict")
                .isEqualTo(versionBefore + 1);

        // (2) A writer holding the stale version (N) must fail optimistic locking.
        TaskJpaEntity stale = new TaskJpaEntity();
        stale.setId(taskId);
        stale.setTenantId(tenantId);
        stale.setProjectId(projectId);
        stale.setTitle("Stale concurrent edit");
        stale.setStatus(TaskStatus.IN_PROGRESS);
        stale.setPriority(TaskPriority.MEDIUM);
        stale.setCreatedAt(Instant.now());
        stale.setUpdatedAt(Instant.now());
        stale.setVersion(versionBefore); // pre-cancel version

        assertThatThrownBy(() ->
                txTemplate.executeWithoutResult(status -> taskRepo.saveAndFlush(stale)))
                .as("A writer holding the pre-cancel version must fail optimistic locking "
                        + "instead of silently overwriting the CANCELLED status")
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String buildProjectArchivedMessage(String eventId, UUID projectId, UUID tenantId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "ProjectArchived",
                  "tenantId": "%s",
                  "occurredAt": "%s",
                  "payload": {
                    "projectId": "%s"
                  }
                }
                """.formatted(eventId, tenantId, Instant.now(), projectId);
    }
}
