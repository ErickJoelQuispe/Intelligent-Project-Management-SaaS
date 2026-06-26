package com.epm.auth.domain.port.in;

/**
 * Driving port: revokes a specific Keycloak session.
 *
 * <p>Implemented by the application layer; invoked by REST adapters.
 */
public interface RevokeSessionUseCase {

    /**
     * Revokes the session identified by {@code sessionId}.
     *
     * @param sessionId the Keycloak session ID to revoke
     * @throws com.epm.auth.domain.exception.IdentityProviderException if the call fails
     */
    void execute(String sessionId);
}
