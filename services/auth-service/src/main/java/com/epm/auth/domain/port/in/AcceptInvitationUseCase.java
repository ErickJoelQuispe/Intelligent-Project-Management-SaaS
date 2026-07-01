package com.epm.auth.domain.port.in;

import com.epm.auth.domain.port.in.command.AcceptInvitationCommand;

/**
 * Driving port: accepts an invitation and creates the invited user's account.
 *
 * <p>Implemented by the application layer; invoked by the REST adapter.
 */
public interface AcceptInvitationUseCase {

    /**
     * Accepts an invitation: validates the token, creates the Keycloak user with the
     * inviting tenant's tenantId and the assigned role (VIEWER), marks the token used,
     * persists the account, and publishes the {@code auth.account.registered} event.
     *
     * @param command accept invitation data
     * @throws com.epm.auth.domain.exception.InvitationTokenInvalidException   if token not found
     * @throws com.epm.auth.domain.exception.InvitationTokenExpiredException   if token expired
     * @throws com.epm.auth.domain.exception.InvitationTokenAlreadyUsedException if token already used
     * @throws com.epm.auth.domain.exception.IdentityProviderException         if Keycloak call fails
     */
    void accept(AcceptInvitationCommand command);
}
