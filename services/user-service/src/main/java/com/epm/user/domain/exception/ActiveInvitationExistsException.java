package com.epm.user.domain.exception;

/**
 * Thrown when an active (not used, not expired) invitation already exists
 * for the given email and tenant combination.
 */
public class ActiveInvitationExistsException extends RuntimeException {

    public ActiveInvitationExistsException(String email, java.util.UUID tenantId) {
        super("An active invitation already exists for email " + email
                + " in tenant " + tenantId);
    }
}
