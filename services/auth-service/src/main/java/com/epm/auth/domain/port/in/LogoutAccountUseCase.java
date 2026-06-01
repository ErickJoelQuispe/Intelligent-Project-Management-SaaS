package com.epm.auth.domain.port.in;

import java.util.UUID;

/**
 * Driving port: logs out an account by invalidating its Keycloak session.
 *
 * <p>Implemented by the application layer; invoked by REST adapters.
 */
public interface LogoutAccountUseCase {

    /**
     * Revokes the session and records a LOGOUT security event.
     *
     * @param accountId   the account's UUID
     * @param tenantId    the account's tenant UUID
     * @param ipAddress   client IP address (for audit log)
     * @param userAgent   client User-Agent (for audit log)
     */
    void logout(UUID accountId, UUID tenantId, String ipAddress, String userAgent);
}
