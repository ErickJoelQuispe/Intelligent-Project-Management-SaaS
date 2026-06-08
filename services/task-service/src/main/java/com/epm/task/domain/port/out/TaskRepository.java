package com.epm.task.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.epm.task.domain.model.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Driven port for task persistence.
 */
public interface TaskRepository {

    Task save(Task task);

    Optional<Task> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Task> findAllByProjectIdAndTenantId(UUID projectId, UUID tenantId, Pageable pageable);

    List<Task> findAllByProjectId(UUID projectId, UUID tenantId);

    List<Task> findSubtasksByParentId(UUID parentTaskId, UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);
}
