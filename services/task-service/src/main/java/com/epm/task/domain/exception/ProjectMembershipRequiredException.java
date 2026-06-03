package com.epm.task.domain.exception;

import java.util.UUID;

/**
 * Thrown when a user attempts to create a task in a project they are not a member of.
 */
public class ProjectMembershipRequiredException extends RuntimeException {

    public ProjectMembershipRequiredException(UUID userId, UUID projectId) {
        super(String.format("User %s is not a member of project %s", userId, projectId));
    }

    public ProjectMembershipRequiredException(String message) {
        super(message);
    }
}
