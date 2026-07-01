package com.epm.user.domain.port.in;

import com.epm.user.domain.model.Invitation;

/**
 * Driving port: validates an invitation token and returns the invitation.
 */
public interface ValidateInvitationUseCase {

    /**
     * Validates the given plaintext token.
     *
     * @param plaintextToken the token from the invitation link
     * @return the matching invitation
     * @throws com.epm.user.domain.exception.InvitationNotFoundException if the token is not found
     * @throws com.epm.user.domain.exception.InvitationExpiredException  if the invitation has expired
     * @throws com.epm.user.domain.exception.InvitationAlreadyUsedException if already accepted
     */
    Invitation validateInvitation(String plaintextToken);
}
