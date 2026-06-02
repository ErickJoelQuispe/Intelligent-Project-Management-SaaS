package com.epm.project.domain.exception;

import java.util.UUID;

/**
 * Thrown when a requested project cannot be found.
 */
public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(UUID projectId) {
        super("Project not found: " + projectId);
    }
}
