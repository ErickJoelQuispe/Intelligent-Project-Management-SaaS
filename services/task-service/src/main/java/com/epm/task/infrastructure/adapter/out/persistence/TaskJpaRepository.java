package com.epm.task.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    @Query("SELECT t FROM TaskJpaEntity t"
            + " WHERE t.projectId = :projectId AND t.tenantId = :tenantId AND t.parentTask IS NULL")
    Page<TaskJpaEntity> findAllByProjectIdAndTenantId(@Param("projectId") UUID projectId,
                                                       @Param("tenantId") UUID tenantId,
                                                       Pageable pageable);

    @Query("SELECT t FROM TaskJpaEntity t"
            + " WHERE t.projectId = :projectId AND t.tenantId = :tenantId AND t.parentTask IS NULL")
    List<TaskJpaEntity> findAllByProjectIdAndTenantId(@Param("projectId") UUID projectId,
                                                       @Param("tenantId") UUID tenantId);

    @Query("SELECT t FROM TaskJpaEntity t WHERE t.parentTask.id = :parentId AND t.tenantId = :tenantId")
    List<TaskJpaEntity> findSubtasksByParentIdAndTenantId(@Param("parentId") UUID parentId,
                                                           @Param("tenantId") UUID tenantId);

    @Modifying
    @Query("DELETE FROM TaskJpaEntity t WHERE t.id = :id AND t.tenantId = :tenantId")
    void deleteByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    /**
     * Bulk-cancels all non-cancelled tasks in the given project.
     *
     * <p>Single UPDATE — no N+1. Returns the count of rows updated.
     * Callers emit ONE aggregate event rather than per-task events.
     *
     * <p>The {@code version} column (mapped by {@link TaskJpaEntity}'s {@code @Version} field)
     * is incremented as part of the SET clause. JPA does NOT bump {@code @Version} for native
     * bulk updates, so this must be done explicitly: without it a concurrent writer that loaded
     * a task at version N would still match its optimistic-lock UPDATE
     * ({@code WHERE version = N}) after the bulk cancel and silently overwrite the CANCELLED
     * status (lost update). Incrementing the version forces the stale writer to fail with an
     * {@code ObjectOptimisticLockingFailureException} (→ HTTP 409) instead.
     */
    @Modifying
    @Query(value = """
            UPDATE tasks
            SET status = 'CANCELLED', updated_at = :now, version = version + 1
            WHERE project_id = :projectId
              AND tenant_id = :tenantId
              AND status <> 'CANCELLED'
            """, nativeQuery = true)
    int bulkCancelByProjectId(@Param("projectId") UUID projectId,
                               @Param("tenantId") UUID tenantId,
                               @Param("now") Instant now);

    /**
     * Bulk-deletes all subtasks of the given parent task.
     *
     * <p>Single DELETE — no N+1. Child deletion is implied by the root {@code TaskDeleted} event.
     */
    @Modifying
    @Query("DELETE FROM TaskJpaEntity t WHERE t.parentTask.id = :parentId AND t.tenantId = :tenantId")
    void bulkDeleteSubtasks(@Param("parentId") UUID parentId, @Param("tenantId") UUID tenantId);
}
