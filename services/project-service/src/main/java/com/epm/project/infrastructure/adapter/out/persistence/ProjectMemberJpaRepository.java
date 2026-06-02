package com.epm.project.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link ProjectMemberJpaEntity}.
 */
public interface ProjectMemberJpaRepository extends JpaRepository<ProjectMemberJpaEntity, UUID> {

    List<ProjectMemberJpaEntity> findByProjectIdAndRemovedAtIsNull(UUID projectId);
}
