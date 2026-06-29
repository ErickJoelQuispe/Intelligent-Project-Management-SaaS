package com.epm.user.domain.exception;

/**
 * Thrown when an invitation token has passed its expiry time.
 */
public class InvitationExpiredException extends RuntimeException {

    public InvitationExpiredException(java.util.UUID invitationId) {
        super("Invitation " + invitationId + " has expired");
    }
}
