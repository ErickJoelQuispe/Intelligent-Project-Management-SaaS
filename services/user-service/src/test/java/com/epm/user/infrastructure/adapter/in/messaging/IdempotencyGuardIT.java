package com.epm.user.infrastructure.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.epm.user.infrastructure.AbstractPostgresIT;
import com.epm.user.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Genuine regression guard for the C1 sequential-duplicate idempotency bug.
 *
 * <p><strong>What this proves:</strong> The idempotency primitive ({@link IdempotencyGuard#claim})
 * returns {@code false} on a sequential duplicate — i.e., when the first claim row is already
 * committed and visible (the normal Kafka redelivery scenario). This is the ONLY unambiguous
 * signal that the {@code Persistable<String>} fix is in place.
 *
 * <p><strong>Why the previous test was tautological:</strong> The old test asserted on business
 * side-effects (profile row count) and processed_events row count. Both are 1 even without the
 * fix: (1) {@code merge()} updates the SAME row, so the count stays at 1; (2) a duplicate profile
 * insert hits the unique-email constraint rather than exposing the idempotency guard failure, so
 * the observed count also stays at 1 for the wrong reason.
 *
 * <p><strong>The genuine RED/GREEN signal:</strong>
 * <ul>
 *   <li>WITHOUT the fix ({@code merge()} path): the second {@code claim()} call silently updates
 *       the existing row and returns {@code true}. The assertion {@code isEqualTo(false)} FAILS.</li>
 *   <li>WITH the fix ({@code persist()} path): the second {@code claim()} triggers a duplicate-PK
 *       {@link org.springframework.dao.DataIntegrityViolationException} in the inner
 *       {@code REQUIRES_NEW} transaction; the guard catches it and returns {@code false}. The test
 *       PASSES.</li>
 * </ul>
 *
 * <p>Requires Docker (Testcontainers + real Flyway). Do NOT weaken to make it pass without Docker.
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
class IdempotencyGuardIT extends AbstractPostgresIT {

    private static final String TEST_TOPIC = "test.idempotency.guard";

    @Autowired
    private IdempotencyGuard guard;

    @Autowired
    private ProcessedEventJpaRepository processedEventRepo;

    @AfterEach
    void cleanup() {
        processedEventRepo.deleteAll();
    }

    /**
     * The primary RED/GREEN signal for C1: second sequential claim must return {@code false}.
     *
     * <p>Step 1: first {@code claim()} — must return {@code true} (claim won, row committed).
     * Step 2: second {@code claim()} with the SAME eventId — must return {@code false} (duplicate
     * detected). Without {@code Persistable}, {@code merge()} would silently UPDATE the existing
     * row and return {@code true}, making this assertion fail.
     * Step 3 (sanity): exactly ONE {@code processed_events} row exists.
     */
    @Test
    void claim_secondSequentialCallWithSameEventId_returnsFalse() {
        String eventId = UUID.randomUUID().toString();

        // First claim — the row does not yet exist; must succeed.
        boolean firstResult = guard.claim(eventId, TEST_TOPIC);
        assertThat(firstResult)
                .as("First claim() must return true — event not seen before")
                .isTrue();

        // Second claim — the first INSERT is already committed and visible (sequential redelivery).
        // WITH fix  (persist path): duplicate PK → DataIntegrityViolationException → returns false.
        // WITHOUT fix (merge path): SELECT finds row → UPDATE (no exception) → returns true. FAILS.
        boolean secondResult = guard.claim(eventId, TEST_TOPIC);
        assertThat(secondResult)
                .as("Second sequential claim() with same eventId must return false — "
                        + "duplicate detected via DataIntegrityViolationException from persist(). "
                        + "If this assertion fails, Persistable<String> is missing and merge() "
                        + "silently updated the row instead of throwing.")
                .isFalse();

        // Sanity: exactly ONE row committed (not two, not zero).
        long rowCount = processedEventRepo.findAll().stream()
                .filter(e -> eventId.equals(e.getEventId()))
                .count();
        assertThat(rowCount)
                .as("Exactly 1 processed_events row must exist for eventId %s", eventId)
                .isEqualTo(1);
    }
}
