package com.epm.user.domain.exception;

/**
 * Thrown when the caller does not have permission to perform an operation.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
