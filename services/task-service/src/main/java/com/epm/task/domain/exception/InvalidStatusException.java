package com.epm.task.domain.exception;

import com.epm.task.domain.model.TaskStatus;

/**
 * Thrown when an invalid or unrecognized task status value is provided, or when
 * a user-driven status transition violates the FSM.
 */
public class InvalidStatusException extends RuntimeException {

    /**
     * Used by the controller when parsing an unrecognised status string from the request.
     *
     * @param status the unrecognised status string
     */
    public InvalidStatusException(String status) {
        super(String.format("Invalid task status: '%s'", status));
    }

    /**
     * Used by the domain FSM when a transition from {@code from} to {@code to} is not allowed.
     *
     * @param from the current task status
     * @param to   the requested new status
     */
    public InvalidStatusException(TaskStatus from, TaskStatus to) {
        super(String.format(
                "Invalid status transition: %s → %s is not allowed by the task FSM", from, to));
    }
}
