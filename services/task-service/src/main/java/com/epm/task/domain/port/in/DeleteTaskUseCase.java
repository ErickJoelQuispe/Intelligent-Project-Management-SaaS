package com.epm.task.domain.port.in;

import java.util.UUID;

/**
 * Driving port: deletes a task and bulk-deletes its subtasks.
 */
public interface DeleteTaskUseCase {

    /**
     * Deletes the task and all its subtasks.
     *
     * @param taskId   root task id
     * @param tenantId tenant scope
     * @param callerId caller subject (used for membership check)
     */
    void execute(UUID taskId, UUID tenantId, UUID callerId);
}
