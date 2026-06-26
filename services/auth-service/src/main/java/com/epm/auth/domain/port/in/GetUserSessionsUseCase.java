package com.epm.auth.domain.port.in;

import java.util.List;
import java.util.UUID;

import com.epm.auth.domain.model.UserSession;

/**
 * Driving port: retrieves all active Keycloak sessions for the authenticated user.
 *
 * <p>Implemented by the application layer; invoked by REST adapters.
 */
public interface GetUserSessionsUseCase {

    /**
     * Returns a list of active sessions for the given Keycloak user.
     *
     * @param keycloakUserId the Keycloak user UUID (JWT {@code sub} claim)
     * @return list of active sessions; may be empty if none found
     */
    List<UserSession> execute(UUID keycloakUserId);
}
