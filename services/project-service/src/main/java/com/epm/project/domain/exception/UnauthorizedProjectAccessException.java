package com.epm.project.domain.exception;

import java.util.UUID;

/**
 * Thrown when a caller does not have sufficient access to a project.
 */
public class UnauthorizedProjectAccessException extends RuntimeException {

    public UnauthorizedProjectAccessException(UUID profileId, UUID projectId) {
        super("Profile " + profileId + " is not authorized to access project " + projectId);
    }
}
