package com.epm.user.infrastructure.adapter.out.persistence;

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
     */
    List<OutboxEventJpaEntity> findTop10ByPublishedAtIsNullAndFailedAtIsNullOrderByCreatedAtAsc();

    /**
     * Fetches the oldest 10 events that failed before the given threshold (for retry).
     */
    List<OutboxEventJpaEntity> findTop10ByPublishedAtIsNullAndFailedAtBeforeOrderByCreatedAtAsc(
            Instant threshold);
}
