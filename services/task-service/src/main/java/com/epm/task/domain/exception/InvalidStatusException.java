package com.epm.task.domain.exception;

/**
 * Thrown when an invalid or unrecognized task status value is provided.
 */
public class InvalidStatusException extends RuntimeException {

    public InvalidStatusException(String status) {
        super(String.format("Invalid task status: '%s'", status));
    }
}
