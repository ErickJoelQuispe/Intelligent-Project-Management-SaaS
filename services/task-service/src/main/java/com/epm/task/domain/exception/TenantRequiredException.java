package com.epm.task.domain.exception;

/**
 * Thrown when an operation is attempted without a required tenant identifier.
 */
public class TenantRequiredException extends RuntimeException {

    public TenantRequiredException() {
        super("tenantId must not be null");
    }

    public TenantRequiredException(String message) {
        super(message);
    }
}
