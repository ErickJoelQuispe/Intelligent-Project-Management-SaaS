package com.epm.task.infrastructure.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link ActivityLogJpaEntity}.
 */
public interface ActivityLogJpaRepository extends JpaRepository<ActivityLogJpaEntity, UUID> {
}
