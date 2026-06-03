package com.epm.task.domain.port.in.command;

import java.time.LocalDate;
import java.util.UUID;

import com.epm.task.domain.model.TaskPriority;

/**
 * Command record for updating an existing task.
 */
public record UpdateTaskCommand(
        UUID taskId,
        UUID tenantId,
        UUID callerId,
        String title,
        String description,
        TaskPriority priority,
        LocalDate deadline) {
}
