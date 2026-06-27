package com.epm.task.infrastructure.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link ActivityLogJpaEntity}.
 */
public interface ActivityLogJpaRepository extends JpaRepository<ActivityLogJpaEntity, UUID> {

    /** Deletes all activity log entries for the given task. */
    @Modifying
    @Query("DELETE FROM ActivityLogJpaEntity a WHERE a.taskId = :taskId")
    void deleteByTaskId(@Param("taskId") UUID taskId);

    /**
     * Deletes activity log entries for all subtasks of the given parent task.
     * Used before bulk-deleting subtask rows to avoid FK violations.
     */
    @Modifying
    @Query(value = "DELETE FROM activity_log WHERE task_id IN " +
                   "(SELECT id FROM tasks WHERE parent_task_id = :parentTaskId AND tenant_id = :tenantId)",
           nativeQuery = true)
    void deleteByParentTaskId(@Param("parentTaskId") UUID parentTaskId, @Param("tenantId") UUID tenantId);
}
