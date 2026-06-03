package com.epm.task.domain.exception;

import java.util.UUID;

/**
 * Thrown when a task cannot be found for the given id and tenant.
 */
public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(UUID taskId) {
        super(String.format("Task not found: %s", taskId));
    }

    public TaskNotFoundException(UUID taskId, UUID tenantId) {
        super(String.format("Task %s not found for tenant %s", taskId, tenantId));
    }
}
