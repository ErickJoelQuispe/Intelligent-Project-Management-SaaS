package com.epm.auth.domain.exception;

/**
 * Thrown when an invitation token has already been accepted (marked used).
 *
 * <p>Pure Java exception — no Spring, no HTTP status annotations.
 * The infrastructure layer maps this to HTTP 409 Conflict.
 */
public class InvitationTokenAlreadyUsedException extends RuntimeException {

    public InvitationTokenAlreadyUsedException(String message) {
        super(message);
    }
}
