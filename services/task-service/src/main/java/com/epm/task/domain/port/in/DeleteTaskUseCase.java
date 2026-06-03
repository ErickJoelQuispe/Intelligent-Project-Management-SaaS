package com.epm.task.domain.port.in;

import java.util.UUID;

/**
 * Driving port: deletes a task and cascade-cancels its subtasks.
 */
public interface DeleteTaskUseCase {

    void execute(UUID taskId, UUID tenantId);
}
