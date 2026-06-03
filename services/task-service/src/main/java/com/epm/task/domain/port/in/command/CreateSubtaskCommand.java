package com.epm.task.domain.port.in.command;

import java.time.LocalDate;
import java.util.UUID;

import com.epm.task.domain.model.TaskPriority;

/**
 * Command record for creating a subtask under an existing root task.
 */
public record CreateSubtaskCommand(
        UUID tenantId,
        UUID projectId,
        UUID parentTaskId,
        UUID callerId,
        String title,
        String description,
        TaskPriority priority,
        LocalDate deadline,
        UUID assigneeId) {
}
