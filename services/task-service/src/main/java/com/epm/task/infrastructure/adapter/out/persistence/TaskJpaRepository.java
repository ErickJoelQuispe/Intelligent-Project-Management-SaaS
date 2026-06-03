package com.epm.task.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link TaskJpaEntity}.
 *
 * <p>All queries include {@code tenantId} filter for multi-tenant isolation.
 */
public interface TaskJpaRepository extends JpaRepository<TaskJpaEntity, UUID> {

    @Query("SELECT t FROM TaskJpaEntity t WHERE t.id = :id AND t.tenantId = :tenantId")
    Optional<TaskJpaEntity> findByIdAndTenantId(@Param("id") UUID id,
                                                 @Param("tenantId") UUID tenantId);

    @Query("SELECT t FROM TaskJpaEntity t WHERE t.projectId = :projectId AND t.tenantId = :tenantId")
    Page<TaskJpaEntity> findAllByProjectIdAndTenantId(@Param("projectId") UUID projectId,
                                                       @Param("tenantId") UUID tenantId,
                                                       Pageable pageable);

    @Query("SELECT t FROM TaskJpaEntity t WHERE t.projectId = :projectId AND t.tenantId = :tenantId")
    List<TaskJpaEntity> findAllByProjectIdAndTenantId(@Param("projectId") UUID projectId,
                                                       @Param("tenantId") UUID tenantId);

    @Query("SELECT t FROM TaskJpaEntity t WHERE t.parentTask.id = :parentId AND t.tenantId = :tenantId")
    List<TaskJpaEntity> findSubtasksByParentIdAndTenantId(@Param("parentId") UUID parentId,
                                                           @Param("tenantId") UUID tenantId);

    @Query("DELETE FROM TaskJpaEntity t WHERE t.id = :id AND t.tenantId = :tenantId")
    @org.springframework.data.jpa.repository.Modifying
    void deleteByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
