package com.epm.auth.domain.exception;

/**
 * Thrown when an invitation token cannot be found in the system.
 *
 * <p>Pure Java exception — no Spring, no HTTP status annotations.
 * The infrastructure layer maps this to HTTP 404.
 */
public class InvitationTokenInvalidException extends RuntimeException {

    public InvitationTokenInvalidException(String message) {
        super(message);
    }
}
