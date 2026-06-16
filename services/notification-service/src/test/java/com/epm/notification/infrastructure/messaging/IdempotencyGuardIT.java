package com.epm.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.epm.notification.infrastructure.AbstractPostgresIT;
import com.epm.notification.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Regression guard for the C1 idempotency mechanism — now built on an ATOMIC
 * {@code INSERT ... ON CONFLICT DO NOTHING} executed in the SAME transaction as the
 * business dispatch ({@link ProcessedEventJpaRepository#claimEvent}).
 *
 * <p>This IT proves the two non-negotiable properties of the redesign:
 *
 * <ol>
 *   <li><strong>Duplicate detection (TOCTOU-safe):</strong> a second {@code claimEvent} for the
 *       same eventId returns {@code 0} rows and the caller skips. Exactly one row exists.</li>
 *   <li><strong>Failure-safety:</strong> when {@code claimEvent} succeeds but the surrounding
 *       transaction later rolls back (simulating a transient dispatch failure), the marker is
 *       NOT persisted. A subsequent redelivery therefore re-processes the event rather than
 *       silently losing the notification. This is the property the previous claim-first design
 *       ({@code IdempotencyGuard} + {@code ProcessedEventClaimer} committing in
 *       {@code REQUIRES_NEW}) VIOLATED: the claim committed ahead of the dispatch, so a rolled-back
 *       dispatch left an orphaned marker and the redelivered event was permanently skipped.</li>
 * </ol>
 *
 * <p><strong>Why this is genuinely RED under claim-first:</strong> the failure-safety test below
 * rolls back the transaction in which {@code claimEvent} ran and asserts the row is GONE. With the
 * atomic same-transaction insert the marker rolls back with the tx (GREEN). With a
 * {@code REQUIRES_NEW} claim it would already be committed and survive the rollback — the
 * assertion {@code count == 0} FAILS.
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
    "spring.security.oauth2.resourceserver.jwt.jwks-uri=https://example.com/.well-known/jwks.json"
})
class IdempotencyGuardIT extends AbstractPostgresIT {

    private static final String TEST_TOPIC = "test.idempotency.guard";

    @Autowired
    private ProcessedEventJpaRepository processedEventRepo;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanup() {
        processedEventRepo.deleteAll();
    }

    /**
     * TOCTOU-safety: the second sequential claim for the same eventId returns {@code 0}.
     *
     * <p>First {@code claimEvent} inserts the row → {@code 1}. Second {@code claimEvent} hits the
     * unique PK and {@code ON CONFLICT DO NOTHING} → {@code 0}. Exactly one row remains.
     */
    @Test
    void claimEvent_secondSequentialCallWithSameEventId_returnsZeroRows() {
        String eventId = UUID.randomUUID().toString();

        int first = transactionTemplate.execute(
                status -> processedEventRepo.claimEvent(eventId, TEST_TOPIC, Instant.now()));
        assertThat(first)
                .as("First claimEvent must insert the marker — 1 row affected")
                .isEqualTo(1);

        int second = transactionTemplate.execute(
                status -> processedEventRepo.claimEvent(eventId, TEST_TOPIC, Instant.now()));
        assertThat(second)
                .as("Second claimEvent with same eventId must affect 0 rows (ON CONFLICT DO NOTHING)")
                .isEqualTo(0);

        long rowCount = processedEventRepo.findAll().stream()
                .filter(e -> eventId.equals(e.getEventId()))
                .count();
        assertThat(rowCount)
                .as("Exactly 1 processed_events row must exist for eventId %s", eventId)
                .isEqualTo(1);
    }

    /**
     * FIX B — cache coherence within the SHARED transaction.
     *
     * <p>The consumer runs {@code claimEvent} (native INSERT) and its JPA business work inside ONE
     * {@code @Transactional} session. This test mirrors that: a single {@link TransactionTemplate}
     * callback runs {@code claimEvent} and THEN a JPA {@code findAll} read — both in the same tx.
     * With {@code @Modifying(flushAutomatically = true, clearAutomatically = true)} the native INSERT
     * is flushed and the L1 cache is cleared, so the subsequent JPA read sees the just-claimed row.
     *
     * <p>This is a coherence guard, not a deterministic RED for the missing-attributes case (forcing
     * an L1-staleness window deterministically is not reliable). The deterministic RED for FIX B is
     * {@code ClaimEventModifyingAttributesTest}, which fails if either attribute is dropped.
     */
    @Test
    void claimEvent_thenJpaReadInSameTransaction_seesTheClaimedRow() {
        String eventId = UUID.randomUUID().toString();

        Boolean visibleInSameTx = transactionTemplate.execute(status -> {
            int claimed = processedEventRepo.claimEvent(eventId, TEST_TOPIC, Instant.now());
            assertThat(claimed)
                    .as("claimEvent must insert the marker (1 row) in this transaction")
                    .isEqualTo(1);

            // Subsequent JPA read in the SAME transaction must observe the just-inserted row.
            return processedEventRepo.findAll().stream()
                    .anyMatch(e -> eventId.equals(e.getEventId()) && TEST_TOPIC.equals(e.getTopic()));
        });

        assertThat(visibleInSameTx)
                .as("A JPA read in the SAME transaction after the native claim must see the row — "
                        + "this requires flushAutomatically (INSERT pushed to DB) + clearAutomatically "
                        + "(L1 cache evicted so the read hits DB truth)")
                .isTrue();
    }

    /**
     * FIX A — idempotency must be scoped per {@code (event_id, topic)}, NOT {@code event_id} alone.
     *
     * <p>The SAME envelope eventId can legitimately arrive on two DIFFERENT source topics
     * (project.events, task.events, user.events) — they are distinct domain events that happen to
     * collide on the generated id. With a single-column PK on {@code event_id}, the second topic's
     * claim hits the conflict and returns {@code 0}, so that event is SILENTLY DROPPED.
     *
     * <p><strong>RED under the old single-column PK:</strong> the second {@code claimEvent} on a
     * different topic returns {@code 0} (conflict on event_id) → assertion {@code isEqualTo(1)}
     * FAILS. With the composite PK {@code (event_id, topic)} both inserts are distinct rows → GREEN.
     */
    @Test
    void claimEvent_sameEventIdDifferentTopics_bothSucceed() {
        String eventId = UUID.randomUUID().toString();

        int onProjectTopic = transactionTemplate.execute(
                status -> processedEventRepo.claimEvent(eventId, "project.events", Instant.now()));
        assertThat(onProjectTopic)
                .as("First claim on project.events must insert (1 row)")
                .isEqualTo(1);

        int onTaskTopic = transactionTemplate.execute(
                status -> processedEventRepo.claimEvent(eventId, "task.events", Instant.now()));
        assertThat(onTaskTopic)
                .as("Same eventId on a DIFFERENT topic must ALSO insert (1 row) — idempotency is "
                        + "scoped per (event_id, topic); a single-column PK would drop this event")
                .isEqualTo(1);

        long rowCount = processedEventRepo.findAll().stream()
                .filter(e -> eventId.equals(e.getEventId()))
                .count();
        assertThat(rowCount)
                .as("Two distinct (event_id, topic) rows must exist for eventId %s", eventId)
                .isEqualTo(2);
    }

    /**
     * FIX A — the SAME {@code (event_id, topic)} pair claimed twice still de-duplicates: the second
     * claim returns {@code 0}. This proves the composite PK did not weaken per-topic idempotency.
     */
    @Test
    void claimEvent_sameEventIdSameTopicTwice_secondReturnsZero() {
        String eventId = UUID.randomUUID().toString();

        int first = transactionTemplate.execute(
                status -> processedEventRepo.claimEvent(eventId, "project.events", Instant.now()));
        assertThat(first)
                .as("First claim on (eventId, project.events) must insert (1 row)")
                .isEqualTo(1);

        int second = transactionTemplate.execute(
                status -> processedEventRepo.claimEvent(eventId, "project.events", Instant.now()));
        assertThat(second)
                .as("Second claim on the SAME (eventId, topic) must affect 0 rows (ON CONFLICT)")
                .isEqualTo(0);
    }

    /**
     * Failure-safety (the property the claim-first design violated): when the transaction that
     * ran {@code claimEvent} rolls back, the marker must NOT survive.
     *
     * <p>We claim the event inside a transaction and then force that same transaction to roll back
     * — exactly what Spring does when the consumer's business dispatch throws a transient error.
     * Because the insert shares the transaction, it rolls back too. A later claim (redelivery)
     * must then SUCCEED ({@code 1}), proving the event is re-processable and not permanently lost.
     */
    @Test
    void claimEvent_whenTransactionRollsBackAfterClaim_markerIsNotPersisted() {
        String eventId = UUID.randomUUID().toString();

        // Claim inside a transaction that is then forcibly rolled back (transient dispatch failure).
        transactionTemplate.execute(status -> {
            int claimed = processedEventRepo.claimEvent(eventId, TEST_TOPIC, Instant.now());
            assertThat(claimed)
                    .as("Claim must succeed before the simulated dispatch failure")
                    .isEqualTo(1);
            status.setRollbackOnly();
            return null;
        });

        // The marker must NOT have been committed — it rolled back with the transaction.
        long survivors = processedEventRepo.findAll().stream()
                .filter(e -> eventId.equals(e.getEventId()))
                .count();
        assertThat(survivors)
                .as("Marker must roll back with the failed transaction — claim-first would leave "
                        + "an orphaned committed row here, permanently skipping the redelivery")
                .isEqualTo(0);

        // Redelivery: the same eventId can be claimed again — the notification is NOT lost.
        int redelivery = transactionTemplate.execute(
                status -> processedEventRepo.claimEvent(eventId, TEST_TOPIC, Instant.now()));
        assertThat(redelivery)
                .as("Redelivery after a rolled-back dispatch must re-claim the event (1 row), "
                        + "proving the event is re-processed rather than lost")
                .isEqualTo(1);
    }
}
