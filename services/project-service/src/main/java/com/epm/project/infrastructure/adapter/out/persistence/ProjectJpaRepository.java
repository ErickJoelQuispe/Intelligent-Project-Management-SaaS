package com.epm.project.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link ProjectJpaEntity}.
 */
public interface ProjectJpaRepository extends JpaRepository<ProjectJpaEntity, UUID> {

    Optional<ProjectJpaEntity> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    @Query("""
            SELECT DISTINCT p FROM ProjectJpaEntity p
            JOIN ProjectMemberJpaEntity m ON p.id = m.projectId
            WHERE m.profileId = :profileId
              AND p.tenantId = :tenantId
              AND p.deletedAt IS NULL
              AND m.removedAt IS NULL
            """)
    List<ProjectJpaEntity> findAllProjectsByMemberProfileId(
            @Param("profileId") UUID profileId,
            @Param("tenantId") UUID tenantId);

    @Query("""
            SELECT DISTINCT p FROM ProjectJpaEntity p
            JOIN ProjectMemberJpaEntity m ON p.id = m.projectId
            WHERE m.profileId = :profileId
              AND p.tenantId = :tenantId
              AND p.deletedAt IS NULL
              AND p.status != 'ARCHIVED'
              AND m.removedAt IS NULL
            """)
    List<ProjectJpaEntity> findAllProjectsByMemberProfileIdExcludingArchived(
            @Param("profileId") UUID profileId,
            @Param("tenantId") UUID tenantId);
}
