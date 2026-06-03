package com.epm.task.application.usecase;

import com.epm.task.domain.model.Task;
import com.epm.task.domain.port.in.result.TaskResult;

/**
 * Utility mapper for converting domain objects to result records.
 */
final class TaskMapper {

    private TaskMapper() {
    }

    static TaskResult toResult(Task task) {
        return new TaskResult(
                task.getId(),
                task.getTenantId(),
                task.getProjectId(),
                task.getParentTaskId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getDeadline(),
                task.getAssigneeId(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }
}
