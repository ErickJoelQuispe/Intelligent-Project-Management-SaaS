package com.epm.auth.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link OutboxEventJpaEntity}.
 */
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    /**
     * Fetches the oldest 10 pending outbox events (no publishedAt and no failedAt).
     *
     * @return list of up to 10 pending events
     */
    List<OutboxEventJpaEntity> findTop10ByPublishedAtIsNullAndFailedAtIsNullOrderByCreatedAtAsc();

    /**
     * Fetches the oldest 10 events that failed before the given threshold (for retry).
     *
     * @param threshold events with failedAt before this instant are eligible for retry
     * @return list of up to 10 retry-eligible events
     */
    List<OutboxEventJpaEntity> findTop10ByPublishedAtIsNullAndFailedAtBeforeOrderByCreatedAtAsc(
            Instant threshold);
}
