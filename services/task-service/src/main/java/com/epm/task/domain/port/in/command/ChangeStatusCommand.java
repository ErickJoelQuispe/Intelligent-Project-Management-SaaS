package com.epm.task.domain.port.in.command;

import java.util.UUID;

import com.epm.task.domain.model.TaskStatus;

/**
 * Command record for changing the status of a task.
 */
public record ChangeStatusCommand(
        UUID taskId,
        UUID tenantId,
        UUID callerId,
        TaskStatus newStatus) {
}
