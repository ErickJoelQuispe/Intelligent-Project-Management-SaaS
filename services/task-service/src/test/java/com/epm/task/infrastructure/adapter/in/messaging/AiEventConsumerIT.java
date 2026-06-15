package com.epm.task.infrastructure.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.epm.task.domain.exception.ProjectServiceUnavailableException;
import com.epm.task.domain.port.out.ProjectMembershipPort;
import com.epm.task.infrastructure.AbstractPostgresIT;
import com.epm.task.infrastructure.adapter.out.persistence.ActivityLogJpaRepository;
import com.epm.task.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import com.epm.task.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import com.epm.task.infrastructure.adapter.out.persistence.TaskJpaRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration tests for {@link AiEventConsumer} against a real PostgreSQL database.
 *
 * <p>Tests cover:
 * <ol>
 *   <li>Valid batch: N tasks created in the DB.</li>
 *   <li>Oversized batch (&gt;50): rejected as poison (no tasks created, no idempotency claim).</li>
 *   <li>Duplicate eventId: second consume is a no-op (idempotency).</li>
 *   <li>Project-service unavailable (infrastructure failure): claim released + rethrow so
 *       Kafka can retry.</li>
 *   <li>AT-LEAST-ONCE — infra failure mid-batch + redelivery: documents that the first k
 *       committed tasks are duplicated on redelivery (accepted trade-off, no per-draft
 *       idempotency).</li>
 * </ol>
 *
 * <p>{@link ProjectMembershipPort} is mocked to decouple from project-service network calls.
 * Kafka is not needed — the consumer is called directly (no broker required).
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
    // The consumer is invoked directly in these tests; no broker exists at localhost:9092.
    // Disable @KafkaListener auto-startup so the listener containers don't burn time retrying
    // a connection to the (intentionally absent) broker.
    "spring.kafka.listener.auto-startup=false",
    // The outbox relay fires AFTER_COMMIT after each task is created and performs a blocking
    // KafkaTemplate.send(). Against a dead broker the producer's default max.block.ms (60s)
    // would block each send for a full minute waiting for topic metadata — turning a 4-task
    // batch into ~4 minutes. KafkaConfig builds the ProducerFactory manually, so the standard
    // spring.kafka.producer.* keys do not apply; drive the bounded values via task.kafka.* so
    // each send fails fast (the relay then marks the row failed) instead of hanging.
    "task.kafka.producer.max-block-ms=500",
    "task.kafka.producer.delivery-timeout-ms=500",
    "spring.security.oauth2.resourceserver.jwt.jwks-uri=https://example.com/.well-known/jwks.json"
})
class AiEventConsumerIT extends AbstractPostgresIT {

    @Autowired
    private AiEventConsumer consumer;

    @Autowired
    private TaskJpaRepository taskRepo;

    @Autowired
    private ActivityLogJpaRepository activityLogRepo;

    @Autowired
    private OutboxEventJpaRepository outboxRepo;

    @Autowired
    private ProcessedEventJpaRepository processedEventRepo;

    @MockitoBean
    private ProjectMembershipPort membershipPort;

    @AfterEach
    void cleanup() {
        // Delete in FK-safe order: activity_log references tasks
        outboxRepo.deleteAll();
        processedEventRepo.deleteAll();
        activityLogRepo.deleteAll();
        taskRepo.deleteAll();
    }

    // ── Scenario (a): valid batch creates N tasks ──────────────────────────────

    @Test
    void consume_validBatch_createsNTasksInDb() {
        when(membershipPort.isMember(any(), any(), any())).thenReturn(true);

        UUID eventId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID generatedBy = UUID.randomUUID();

        String message = buildAiMessage(eventId, projectId, tenantId, generatedBy, 3);
        consumer.consume(message);

        // 3 tasks should exist in the DB
        long taskCount = taskRepo.findAll().stream()
                .filter(t -> t.getTenantId().equals(tenantId) && t.getProjectId().equals(projectId))
                .count();
        assertThat(taskCount)
                .as("3 tasks must be created for the valid batch")
                .isEqualTo(3);

        // 1 processed_events row committed
        assertThat(processedEventRepo.existsByEventId(eventId.toString()))
                .as("Processed event claim must exist")
                .isTrue();
    }

    // ── Scenario (b): oversized batch rejected as poison ──────────────────────

    @Test
    void consume_oversizedBatch_discardedAsPoison_noTasksCreated() {
        UUID eventId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID generatedBy = UUID.randomUUID();

        // 51 drafts — one over the MAX_AI_BATCH_SIZE limit
        String message = buildAiMessage(eventId, projectId, tenantId, generatedBy, 51);
        consumer.consume(message);

        // No tasks, no idempotency claim (poison rejected before claim)
        assertThat(taskRepo.findAll()).isEmpty();
        assertThat(processedEventRepo.existsByEventId(eventId.toString())).isFalse();
    }

    // ── Scenario (c): duplicate eventId skipped ───────────────────────────────

    @Test
    void consume_duplicateEventId_secondCallIsNoOp() {
        when(membershipPort.isMember(any(), any(), any())).thenReturn(true);

        UUID eventId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID generatedBy = UUID.randomUUID();

        String message = buildAiMessage(eventId, projectId, tenantId, generatedBy, 2);

        consumer.consume(message); // first: creates 2 tasks
        consumer.consume(message); // second: duplicate, should skip

        long taskCount = taskRepo.findAll().stream()
                .filter(t -> t.getTenantId().equals(tenantId) && t.getProjectId().equals(projectId))
                .count();
        assertThat(taskCount)
                .as("Only 2 tasks must exist — second consume must be skipped as duplicate")
                .isEqualTo(2);

        // Exactly 1 processed_events row (not 2)
        long claimedCount = processedEventRepo.findAll().stream()
                .filter(p -> eventId.toString().equals(p.getEventId()))
                .count();
        assertThat(claimedCount)
                .as("Exactly 1 processed_events row for the eventId")
                .isEqualTo(1);
    }

    // ── Scenario (d): project-service unavailable → claim released + rethrow ──

    @Test
    void consume_projectServiceUnavailable_releasesClaimAndRethrows() {
        // All membership checks throw ProjectServiceUnavailableException (circuit open)
        doThrow(new ProjectServiceUnavailableException("circuit breaker open"))
                .when(membershipPort).isMember(any(), any(), any());

        UUID eventId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID generatedBy = UUID.randomUUID();

        String message = buildAiMessage(eventId, projectId, tenantId, generatedBy, 1);

        // Must rethrow (triggering Kafka retry / DLT)
        assertThatThrownBy(() -> consumer.consume(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Infrastructure failure");

        // Claim must have been released (deleted) so redelivery can retry
        assertThat(processedEventRepo.existsByEventId(eventId.toString()))
                .as("Idempotency claim must be released after infrastructure failure so redelivery can retry")
                .isFalse();

        // No tasks were created
        assertThat(taskRepo.findAll()).isEmpty();
    }

    // ── Scenario (e): AT-LEAST-ONCE — redelivery after infra failure mid-batch ─
    //
    // This test DOCUMENTS ACCEPTED BEHAVIOR, not a bug to be fixed.
    //
    // Design: the idempotency claim is committed in REQUIRES_NEW before the per-draft
    // loop. Each draft's task creation is its own committed transaction. If an infra
    // failure hits draft k+1, the first k tasks are already committed and cannot be
    // rolled back. The consumer releases the claim (so Kafka can retry) and rethrows.
    // On redelivery the whole batch is replayed, duplicating the first k tasks.
    //
    // This is AT-LEAST-ONCE processing: an accepted trade-off because AI drafts are
    // human-curated. Per-draft idempotency (eventId+draftIndex composite key) is the
    // future path to exactly-once if duplicates become a problem.

    @Test
    void consume_infraFailureMidBatch_redeliveryDuplicatesAlreadyCreatedTasks() {
        UUID eventId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID generatedBy = UUID.randomUUID();

        // Build a batch of 3 drafts.
        // The membership port will succeed for draft 1 and 2, then throw on draft 3.
        // This simulates an infrastructure failure mid-batch (circuit breaker / network blip).
        int totalDrafts = 3;
        AtomicInteger callCount = new AtomicInteger(0);
        doAnswer(invocation -> {
            int call = callCount.incrementAndGet();
            if (call >= totalDrafts) {
                // Third membership check (and beyond) fails with an infrastructure exception.
                throw new ProjectServiceUnavailableException("circuit breaker open on draft " + call);
            }
            return true; // drafts 1 and 2 succeed
        }).when(membershipPort).isMember(any(), any(), any());

        String message = buildAiMessage(eventId, projectId, tenantId, generatedBy, totalDrafts);

        // ── First consume: 2 tasks committed, infra failure on draft 3, claim released ──
        assertThatThrownBy(() -> consumer.consume(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Infrastructure failure");

        long tasksAfterFirstConsume = taskRepo.findAll().stream()
                .filter(t -> t.getTenantId().equals(tenantId) && t.getProjectId().equals(projectId))
                .count();
        assertThat(tasksAfterFirstConsume)
                .as("2 tasks must be committed before the infra failure on draft 3")
                .isEqualTo(2);

        // Claim must have been released so redelivery is possible.
        assertThat(processedEventRepo.existsByEventId(eventId.toString()))
                .as("Claim must be released after infra failure so Kafka can redeliver")
                .isFalse();

        // ── Reset: membership now succeeds for all drafts (infrastructure recovered) ──
        callCount.set(0);
        when(membershipPort.isMember(any(), any(), any())).thenReturn(true);

        // ── Redelivery (same message, same eventId): full batch replayed from scratch ──
        // The first 2 tasks (already in DB) are created AGAIN — this is the documented
        // AT-LEAST-ONCE behavior. The consumer has no per-draft idempotency, so the
        // already-committed tasks are duplicated.
        consumer.consume(message);

        long tasksAfterRedelivery = taskRepo.findAll().stream()
                .filter(t -> t.getTenantId().equals(tenantId) && t.getProjectId().equals(projectId))
                .count();

        // DOCUMENTED AT-LEAST-ONCE: 2 (from first consume) + 3 (from full redelivery) = 5.
        // This documents that the current design is at-least-once for AI draft batches.
        // If this number ever drops to 3 it means per-draft idempotency was implemented —
        // update this test to reflect the new exactly-once guarantee.
        assertThat(tasksAfterRedelivery)
                .as("AT-LEAST-ONCE: the 2 tasks committed before the failure are duplicated "
                        + "on redelivery because there is no per-draft idempotency. "
                        + "Total = 2 (first consume) + 3 (full redelivery) = 5.")
                .isEqualTo(5);

        // The idempotency claim exists for the completed redelivery (whole-event dedup).
        assertThat(processedEventRepo.existsByEventId(eventId.toString()))
                .as("Claim must exist after successful redelivery")
                .isTrue();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildAiMessage(UUID eventId, UUID projectId, UUID tenantId,
            UUID generatedBy, int draftCount) {
        StringBuilder tasks = new StringBuilder("[");
        for (int i = 0; i < draftCount; i++) {
            tasks.append("""
                    {"title":"AI Task %d","description":"Generated task %d","priority":"MEDIUM"}"""
                    .formatted(i + 1, i + 1));
            if (i < draftCount - 1) tasks.append(",");
        }
        tasks.append("]");

        return """
                {
                  "eventId": "%s",
                  "eventType": "AiTasksGenerated",
                  "tenantId": "%s",
                  "occurredAt": "%s",
                  "payload": {
                    "projectId": "%s",
                    "generatedBy": "%s",
                    "tasks": %s
                  }
                }
                """.formatted(eventId, tenantId, Instant.now(), projectId, generatedBy, tasks);
    }
}
