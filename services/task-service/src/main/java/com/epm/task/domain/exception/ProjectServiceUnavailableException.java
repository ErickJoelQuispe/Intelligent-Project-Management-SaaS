package com.epm.task.domain.exception;

/**
 * Thrown when the project-service is unavailable (circuit breaker open or timeout exceeded).
 */
public class ProjectServiceUnavailableException extends RuntimeException {

    public ProjectServiceUnavailableException() {
        super("project-service is currently unavailable — please retry later");
    }

    public ProjectServiceUnavailableException(String message) {
        super(message);
    }

    public ProjectServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
