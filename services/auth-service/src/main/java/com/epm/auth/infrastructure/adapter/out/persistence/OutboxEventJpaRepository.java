package com.epm.auth.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link OutboxEventJpaEntity}.
 *
 * <p>Uses pessimistic locking with {@code FOR UPDATE SKIP LOCKED} to prevent double-publish
 * races between the {@code @TransactionalEventListener} (post-commit trigger) and the
 * {@code @Scheduled} safety-net in {@code OutboxRelayService}. Concurrent relay runners
 * will skip rows already held by another transaction rather than processing them twice.
 *
 * <p>The {@code LIMIT 10} cap ensures bounded batch sizes under high throughput.
 */
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    /**
     * Fetches and locks the oldest 10 pending outbox events (no publishedAt and no failedAt).
     *
     * <p>Uses {@code FOR UPDATE SKIP LOCKED}: concurrent callers skip rows locked by this
     * query, preventing double-publish without blocking.
     *
     * @return list of up to 10 pending events, exclusively locked for the current transaction
     */
    @Query(value = "SELECT * FROM outbox_events "
            + "WHERE published_at IS NULL AND failed_at IS NULL "
            + "ORDER BY created_at ASC LIMIT 10 "
            + "FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<OutboxEventJpaEntity> lockPendingBatch();

    /**
     * Fetches and locks the oldest 10 events eligible for retry (failed before the threshold).
     *
     * <p>Uses {@code FOR UPDATE SKIP LOCKED}: concurrent callers skip rows locked by this
     * query, preventing duplicate retry processing.
     *
     * @param threshold events with {@code failed_at} before this instant are eligible
     * @return list of up to 10 retry-eligible events, exclusively locked for the current transaction
     */
    @Query(value = "SELECT * FROM outbox_events "
            + "WHERE published_at IS NULL AND failed_at < :threshold "
            + "ORDER BY created_at ASC LIMIT 10 "
            + "FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<OutboxEventJpaEntity> lockRetryBatch(@Param("threshold") Instant threshold);
}
