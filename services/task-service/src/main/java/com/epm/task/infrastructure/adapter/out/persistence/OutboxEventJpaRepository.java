package com.epm.task.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link OutboxEventJpaEntity}.
 */
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    List<OutboxEventJpaEntity> findTop10ByPublishedAtIsNullAndFailedAtIsNullOrderByCreatedAtAsc();

    List<OutboxEventJpaEntity> findTop10ByPublishedAtIsNullAndFailedAtBeforeOrderByCreatedAtAsc(
            Instant threshold);
}
