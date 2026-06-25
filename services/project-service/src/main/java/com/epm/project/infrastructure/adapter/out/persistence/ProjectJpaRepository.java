package com.epm.project.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link ProjectJpaEntity}.
 */
public interface ProjectJpaRepository extends JpaRepository<ProjectJpaEntity, UUID> {

    Optional<ProjectJpaEntity> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    /**
     * Updates mutable project fields directly via SQL, bypassing optimistic locking.
     * Used by {@code ProjectPersistenceAdapter.save()} for update operations so that
     * concurrent reads with a stale {@code @Version} never cause a false 409.
     * The version column is incremented manually to keep the audit trail consistent.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ProjectJpaEntity p
               SET p.name        = :name,
                   p.description = :description,
                   p.status      = :status,
                   p.visibility  = :visibility,
                   p.updatedAt   = :updatedAt,
                   p.updatedBy   = 'system',
                   p.deletedAt   = :deletedAt,
                   p.version     = p.version + 1
             WHERE p.id = :id
            """)
    int updateFields(
            @Param("id")          UUID    id,
            @Param("name")        String  name,
            @Param("description") String  description,
            @Param("status")      String  status,
            @Param("visibility")  String  visibility,
            @Param("updatedAt")   Instant updatedAt,
            @Param("deletedAt")   Instant deletedAt);

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

    /**
     * Paginated variant of {@link #findAllProjectsByMemberProfileId}. The {@link Pageable}
     * pushes LIMIT/OFFSET to SQL so only one page of projects is materialised. Ordered by
     * {@code createdAt} so paging is stable across requests.
     */
    @Query("""
            SELECT DISTINCT p FROM ProjectJpaEntity p
            JOIN ProjectMemberJpaEntity m ON p.id = m.projectId
            WHERE m.profileId = :profileId
              AND p.tenantId = :tenantId
              AND p.deletedAt IS NULL
              AND m.removedAt IS NULL
            ORDER BY p.createdAt
            """)
    List<ProjectJpaEntity> findPageByMemberProfileId(
            @Param("profileId") UUID profileId,
            @Param("tenantId") UUID tenantId,
            Pageable pageable);

    /**
     * Paginated variant of {@link #findAllProjectsByMemberProfileIdExcludingArchived}. The
     * {@link Pageable} pushes LIMIT/OFFSET to SQL. Ordered by {@code createdAt} for stable paging.
     */
    @Query("""
            SELECT DISTINCT p FROM ProjectJpaEntity p
            JOIN ProjectMemberJpaEntity m ON p.id = m.projectId
            WHERE m.profileId = :profileId
              AND p.tenantId = :tenantId
              AND p.deletedAt IS NULL
              AND p.status != 'ARCHIVED'
              AND m.removedAt IS NULL
            ORDER BY p.createdAt
            """)
    List<ProjectJpaEntity> findPageByMemberProfileIdExcludingArchived(
            @Param("profileId") UUID profileId,
            @Param("tenantId") UUID tenantId,
            Pageable pageable);
}
