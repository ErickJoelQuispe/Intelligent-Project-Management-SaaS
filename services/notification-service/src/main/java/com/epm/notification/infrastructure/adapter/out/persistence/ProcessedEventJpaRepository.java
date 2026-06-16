package com.epm.notification.infrastructure.adapter.out.persistence;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link ProcessedEventJpaEntity}.
 */
public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventJpaEntity, ProcessedEventId> {

    /**
     * Atomically claims an event for processing using PostgreSQL
     * {@code INSERT ... ON CONFLICT DO NOTHING}.
     *
     * <p>This is the single source of idempotency truth. It runs in the SAME transaction as
     * the caller's business dispatch, which gives BOTH safety properties at once:
     * <ul>
     *   <li><strong>TOCTOU-safety:</strong> two concurrent deliveries of the same
     *       {@code (eventId, topic)} race on the {@code pk_processed_events} composite primary key;
     *       exactly one wins ({@code rows == 1}), the other gets {@code rows == 0} and skips — no
     *       check-then-act gap.</li>
     *   <li><strong>Failure-safety:</strong> because the insert shares the caller's transaction,
     *       a later dispatch failure (transient deadlock, dropped connection) rolls the marker
     *       back WITH the business work. On Kafka redelivery the event is re-processed instead of
     *       being permanently skipped — the claim is never committed ahead of the dispatch.</li>
     * </ul>
     *
     * <p><strong>Idempotency scope (FIX A):</strong> the conflict target is the composite key
     * {@code (event_id, topic)}, NOT {@code event_id} alone. The same envelope eventId can arrive
     * on two different source topics as distinct domain events; a single-column conflict target
     * would drop the second one.
     *
     * <p><strong>Cache coherence (FIX B):</strong> {@code @Modifying(clearAutomatically = true,
     * flushAutomatically = true)} is mandatory because this native INSERT shares ONE transaction
     * with the consumer's business dispatch. {@code flushAutomatically} forces pending dirty entities
     * to the DB before the native INSERT runs (correct ordering / no reordering surprises);
     * {@code clearAutomatically} evicts Hibernate's L1 cache so a subsequent JPA read in the same
     * transaction sees DB truth rather than a stale first-level snapshot.
     *
     * @param eventId     the envelope eventId (part of the composite primary key)
     * @param topic       the source topic (part of the composite primary key)
     * @param processedAt the claim timestamp
     * @return {@code 1} if this call inserted the marker (event not seen before on this topic);
     *         {@code 0} if the marker already existed (benign duplicate — skip)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO processed_events (event_id, topic, processed_at)
            VALUES (:eventId, :topic, :processedAt)
            ON CONFLICT (event_id, topic) DO NOTHING
            """, nativeQuery = true)
    int claimEvent(@Param("eventId") String eventId,
            @Param("topic") String topic,
            @Param("processedAt") Instant processedAt);
}
