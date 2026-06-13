package com.epm.template.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link OutboxEventJpaEntity}.
 *
 * <p>Both relay queries use {@code FOR UPDATE SKIP LOCKED} to prevent concurrent
 * relay threads (or pods) from picking up the same event. Each thread locks only
 * the rows it can acquire without waiting, skipping any row already held by another
 * thread — ensuring at-most-once delivery attempt per batch cycle.
 */
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    /**
     * Fetches the oldest 10 pending outbox events (no {@code published_at} and no {@code failed_at}),
     * locking each row with {@code FOR UPDATE SKIP LOCKED} to prevent duplicate relay.
     *
     * @return up to 10 pending events, ordered oldest-first
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL
              AND failed_at IS NULL
            ORDER BY created_at ASC
            LIMIT 10
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventJpaEntity> lockPendingBatch();

    /**
     * Fetches the oldest 10 events that failed before the given cooldown threshold
     * (eligible for retry), locking each row with {@code FOR UPDATE SKIP LOCKED}.
     *
     * @param threshold events with {@code failed_at} before this instant are eligible for retry
     * @return up to 10 retry-eligible events, ordered oldest-first
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL
              AND failed_at < :threshold
            ORDER BY created_at ASC
            LIMIT 10
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventJpaEntity> lockRetryBatch(@Param("threshold") Instant threshold);
}
