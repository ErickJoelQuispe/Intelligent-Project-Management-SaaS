package com.epm.task.infrastructure.adapter.out.persistence;

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
 * relay threads from picking up the same event (each thread locks only the rows
 * it can acquire without waiting, skipping rows already locked by another thread).
 */
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    /**
     * Fetches the oldest 10 pending outbox events (no publishedAt and no failedAt),
     * locking each row with {@code FOR UPDATE SKIP LOCKED} to prevent duplicate relay.
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
     * @param threshold events with {@code failed_at} before this instant are eligible
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
