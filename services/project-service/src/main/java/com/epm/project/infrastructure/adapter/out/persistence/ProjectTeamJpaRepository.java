package com.epm.project.infrastructure.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link ProjectTeamJpaEntity}.
 */
public interface ProjectTeamJpaRepository extends JpaRepository<ProjectTeamJpaEntity, UUID> {

    Optional<ProjectTeamJpaEntity> findByProjectIdAndTeamIdAndOrphanedAtIsNull(UUID projectId, UUID teamId);

    List<ProjectTeamJpaEntity> findByProjectIdAndOrphanedAtIsNull(UUID projectId);

    /**
     * Finds active team assignments for a given team, filtered by tenant (FIX 8/FIX 19).
     * Pushes the tenant filter into SQL to avoid in-memory filtering.
     */
    List<ProjectTeamJpaEntity> findByTeamIdAndTenantIdAndOrphanedAtIsNull(UUID teamId, UUID tenantId);

    /**
     * Legacy — kept for backward compatibility; prefer the tenant-filtered overload above.
     */
    List<ProjectTeamJpaEntity> findByTeamIdAndOrphanedAtIsNull(UUID teamId);

    /**
     * Batch-loads all active team assignments for the given project IDs in a single query
     * (FIX 8 — eliminates N per-project team queries on list operations).
     */
    List<ProjectTeamJpaEntity> findByProjectIdInAndOrphanedAtIsNull(Collection<UUID> projectIds);
}
