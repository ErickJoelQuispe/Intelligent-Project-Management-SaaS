package com.epm.task.domain.port.in.command;

import java.util.UUID;

/**
 * Command record for assigning a task to a user.
 */
public record AssignTaskCommand(
        UUID taskId,
        UUID tenantId,
        UUID callerId,
        UUID assigneeId) {
}
