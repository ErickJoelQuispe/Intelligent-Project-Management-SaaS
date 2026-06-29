package com.epm.user.domain.exception;

/**
 * Thrown when an invitation cannot be found by token or ID.
 */
public class InvitationNotFoundException extends RuntimeException {

    public InvitationNotFoundException(String message) {
        super(message);
    }
}
