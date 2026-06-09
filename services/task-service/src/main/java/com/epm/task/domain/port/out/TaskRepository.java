package com.epm.task.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.epm.task.domain.model.PageResult;
import com.epm.task.domain.model.Task;

/**
 * Driven port for task persistence.
 */
public interface TaskRepository {

    Task save(Task task);

    Optional<Task> findByIdAndTenantId(UUID id, UUID tenantId);

    PageResult<Task> findAllByProjectIdAndTenantId(UUID projectId, UUID tenantId, int page, int size);

    List<Task> findAllByProjectId(UUID projectId, UUID tenantId);

    List<Task> findSubtasksByParentId(UUID parentTaskId, UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);
}
