package com.epm.ai.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link OutboxEventJpaEntity}.
 */
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    /**
     * Claims up to 10 pending (never-published, never-failed, not-parked) events,
     * skipping rows already locked by a concurrent relay.
     *
     * <p><b>H1 fix:</b> {@code FOR UPDATE SKIP LOCKED} prevents the immediate
     * (AFTER_COMMIT) path and the scheduled poller from selecting and publishing
     * the same row at the same time. Must be called inside a transaction so the
     * row locks are held until the status update commits.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL AND failed_at IS NULL AND parked = FALSE
            ORDER BY created_at ASC
            LIMIT 10
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventJpaEntity> lockPendingForRelay();

    /**
     * Claims up to 10 failed events whose retry threshold has passed, skipping
     * rows already locked and excluding parked (poison) events.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL AND parked = FALSE AND failed_at < :threshold
            ORDER BY created_at ASC
            LIMIT 10
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventJpaEntity> lockFailedForRetry(@Param("threshold") Instant threshold);
}
