package com.epm.task.infrastructure.adapter.in.web;

import java.util.UUID;

/**
 * Request DTO for assigning a task to a user.
 *
 * <p>assigneeId may be null to clear the current assignee.
 */
public record AssignTaskRequest(UUID assigneeId) {
}
