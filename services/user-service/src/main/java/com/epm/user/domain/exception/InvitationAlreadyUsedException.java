package com.epm.user.domain.exception;

/**
 * Thrown when an invitation has already been accepted (usedAt is set).
 */
public class InvitationAlreadyUsedException extends RuntimeException {

    public InvitationAlreadyUsedException(java.util.UUID invitationId) {
        super("Invitation " + invitationId + " has already been used");
    }
}
