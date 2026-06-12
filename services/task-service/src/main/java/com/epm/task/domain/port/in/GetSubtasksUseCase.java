package com.epm.task.domain.port.in;

import java.util.List;
import java.util.UUID;

import com.epm.task.domain.model.Task;

/**
 * Driving port: retrieves all subtasks for a given parent task.
 */
public interface GetSubtasksUseCase {

    List<Task> getSubtasks(UUID parentTaskId, UUID tenantId);
}
