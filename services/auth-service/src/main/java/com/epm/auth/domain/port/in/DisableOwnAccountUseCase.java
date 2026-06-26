package com.epm.auth.domain.port.in;

import java.util.UUID;

/**
 * Driving port: disables the authenticated user's own account in the identity provider.
 *
 * <p>First step of the frontend-orchestrated account deletion flow. Sets the Keycloak
 * user to {@code enabled=false}, preventing future logins before the user-service
 * soft-delete is performed.
 *
 * <p>Implemented by the application layer; invoked by REST adapters.
 */
public interface DisableOwnAccountUseCase {

    /**
     * Disables the Keycloak user identified by {@code keycloakUserId}.
     *
     * @param keycloakUserId the Keycloak user UUID (JWT {@code sub} claim)
     * @throws com.epm.auth.domain.exception.IdentityProviderException if the call fails
     */
    void execute(UUID keycloakUserId);
}
