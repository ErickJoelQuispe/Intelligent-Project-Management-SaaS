package com.epm.task.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link KanbanViewJpaEntity}.
 *
 * <p>Reads from the {@code task_kanban_view} materialized view.
 */
public interface KanbanViewJpaRepository extends JpaRepository<KanbanViewJpaEntity, UUID> {

    @Query(value = "SELECT * FROM task_kanban_view WHERE project_id = :projectId AND tenant_id = :tenantId",
            nativeQuery = true)
    List<KanbanViewJpaEntity> findByProjectIdAndTenantId(@Param("projectId") UUID projectId,
                                                          @Param("tenantId") UUID tenantId);
}
