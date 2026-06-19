package com.epm.project.infrastructure.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;

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

import com.epm.project.infrastructure.AbstractPostgresIT;
import com.epm.project.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import com.epm.project.infrastructure.adapter.out.persistence.ProjectJpaEntity;
import com.epm.project.infrastructure.adapter.out.persistence.ProjectJpaRepository;
import com.epm.project.infrastructure.adapter.out.persistence.ProjectMemberJpaEntity;
import com.epm.project.infrastructure.adapter.out.persistence.ProjectMemberJpaRepository;
import com.epm.project.infrastructure.adapter.out.persistence.ProjectTeamJpaEntity;
import com.epm.project.infrastructure.adapter.out.persistence.ProjectTeamJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Concurrent idempotency IT for {@link TeamDeletedConsumer}.
 *
 * <p>Two threads invoke {@link TeamDeletedConsumer#consume(String)} DIRECTLY with the
 * SAME eventId, released together via a start-gate so they race as concurrently as
 * possible. We call the injected bean (not {@code this}) so the {@code @Transactional}
 * proxy opens an independent transaction per call — which is exactly the concurrent flow
 * that exercises the processed_events PK backstop.
 *
 * <p>Asserts:
 * <ol>
 *   <li>NEITHER task threw — with the {@code REQUIRES_NEW} {@link IdempotencyGuard} the
 *       race loser skips benignly and the consumer transaction is never poisoned, so no
 *       {@code UnexpectedRollbackException} escapes either thread.</li>
 *   <li>The project-team assignment is orphaned (business logic applied exactly once).</li>
 *   <li>Exactly ONE {@code processed_events} row exists for the eventId.</li>
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
class TeamDeletedConsumerIT extends AbstractPostgresIT {

    @Autowired
    private TeamDeletedConsumer consumer;

    @Autowired
    private ProjectJpaRepository projectRepo;

    @Autowired
    private ProjectTeamJpaRepository teamRepo;

    @Autowired
    private ProjectMemberJpaRepository memberRepo;

    @Autowired
    private ProcessedEventJpaRepository processedEventRepo;

    private UUID tenantId;
    private UUID teamId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        teamId = UUID.randomUUID();
        projectId = UUID.randomUUID();

        ProjectJpaEntity project = new ProjectJpaEntity();
        project.setId(projectId);
        project.setTenantId(tenantId);
        project.setOwnerId(UUID.randomUUID());
        project.setName("Concurrent Test Project");
        project.setStatus("ACTIVE");
        project.setVisibility("PRIVATE");
        project.setCreatedAt(Instant.now());
        project.setUpdatedAt(Instant.now());
        project.setCreatedBy("system");
        project.setUpdatedBy("system");
        projectRepo.save(project);

        ProjectTeamJpaEntity teamAssignment = new ProjectTeamJpaEntity();
        teamAssignment.setId(UUID.randomUUID());
        teamAssignment.setProjectId(projectId);
        teamAssignment.setTeamId(teamId);
        teamAssignment.setTenantId(tenantId);
        teamAssignment.setAssignedAt(Instant.now());
        teamAssignment.setCreatedAt(Instant.now());
        teamAssignment.setUpdatedAt(Instant.now());
        teamAssignment.setCreatedBy("system");
        teamAssignment.setUpdatedBy("system");
        teamRepo.save(teamAssignment);

        ProjectMemberJpaEntity member = new ProjectMemberJpaEntity();
        member.setId(UUID.randomUUID());
        member.setProjectId(projectId);
        member.setProfileId(UUID.randomUUID());
        member.setRole("OWNER");
        member.setTenantId(tenantId);
        member.setJoinedAt(Instant.now());
        member.setCreatedAt(Instant.now());
        member.setUpdatedAt(Instant.now());
        member.setCreatedBy("system");
        member.setUpdatedBy("system");
        memberRepo.save(member);
    }

    @AfterEach
    void cleanup() {
        processedEventRepo.deleteAll();
        teamRepo.deleteAll();
        memberRepo.deleteAll();
        projectRepo.deleteAll();
    }

    /**
     * Regression guard for the REQUIRES_NEW idempotency-guard fix (rollback poisoning).
     *
     * <p>Two threads race on the SAME eventId. The {@link IdempotencyGuard} claims the
     * processed_events row in its OWN {@code REQUIRES_NEW} transaction, so the loser's
     * {@code DataIntegrityViolationException} is confined to that inner transaction and the
     * consumer's transaction is never marked rollback-only. Therefore NEITHER thread throws
     * (no {@code UnexpectedRollbackException} at commit), exactly one processed_events row
     * exists, and the assignment is orphaned exactly once.
     */
    @Test
    void consume_concurrentSameEventId_exactlyOneProcessedRow_noExceptionEscapes() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String message = buildTeamDeletedMessage(eventId, teamId, tenantId);

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

            // Release both threads simultaneously.
            startGate.countDown();
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        // NEITHER task threw: the REQUIRES_NEW guard prevents rollback poisoning, so the
        // race loser skips benignly instead of throwing UnexpectedRollbackException at commit.
        assertThat(threwCount.get())
                .as("No concurrent task may throw — REQUIRES_NEW guard prevents rollback poisoning")
                .isZero();

        // Exactly ONE processed_events row for the eventId.
        long processedCount = processedEventRepo.findAll().stream()
                .filter(p -> eventId.equals(p.getEventId()))
                .count();
        assertThat(processedCount)
                .as("Expected exactly 1 processed_events row for eventId %s", eventId)
                .isEqualTo(1);

        // The team assignment must be orphaned exactly once.
        long orphanedCount = teamRepo.findAll().stream()
                .filter(t -> t.getTeamId().equals(teamId) && t.getOrphanedAt() != null)
                .count();
        assertThat(orphanedCount)
                .as("Team assignment must be orphaned exactly once")
                .isEqualTo(1);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String buildTeamDeletedMessage(String eventId, UUID teamId, UUID tenantId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "TeamDeleted",
                  "eventVersion": 1,
                  "tenantId": "%s",
                  "occurredAt": "%s",
                  "payload": {
                    "teamId": "%s"
                  }
                }
                """.formatted(eventId, tenantId, Instant.now(), teamId);
    }
}
