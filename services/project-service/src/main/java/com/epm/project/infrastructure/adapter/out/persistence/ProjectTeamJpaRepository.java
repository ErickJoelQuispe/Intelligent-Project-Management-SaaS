package com.epm.project.infrastructure.adapter.out.persistence;

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

    List<ProjectTeamJpaEntity> findByTeamIdAndOrphanedAtIsNull(UUID teamId);
}
