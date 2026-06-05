package com.epm.ai.domain.exception;

/**
 * Thrown when the project-service cannot find the requested project.
 */
public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(String projectId) {
        super("Project not found: " + projectId);
    }
}
