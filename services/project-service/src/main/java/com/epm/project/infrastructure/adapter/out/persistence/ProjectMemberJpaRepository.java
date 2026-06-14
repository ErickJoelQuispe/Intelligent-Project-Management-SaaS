package com.epm.project.infrastructure.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link ProjectMemberJpaEntity}.
 */
public interface ProjectMemberJpaRepository extends JpaRepository<ProjectMemberJpaEntity, UUID> {

    List<ProjectMemberJpaEntity> findByProjectIdAndRemovedAtIsNull(UUID projectId);

    /**
     * Batch-loads all active members for the given project IDs in a single query
     * (FIX 8 — eliminates N per-project member queries on list operations).
     */
    List<ProjectMemberJpaEntity> findByProjectIdInAndRemovedAtIsNull(Collection<UUID> projectIds);
}
