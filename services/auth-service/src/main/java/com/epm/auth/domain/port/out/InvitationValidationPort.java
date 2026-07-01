package com.epm.auth.domain.port.out;

import java.util.UUID;

/**
 * Driven port: validates an invitation token and marks it as used.
 *
 * <p>Implemented in the infrastructure layer by a Feign client adapter that
 * calls the user-service over Eureka-discovered HTTP.
 *
 * <p>Domain exceptions thrown by implementations:
 * <ul>
 *   <li>{@link com.epm.auth.domain.exception.InvitationTokenInvalidException} — token not found (404)</li>
 *   <li>{@link com.epm.auth.domain.exception.InvitationTokenExpiredException} — token expired (410)</li>
 *   <li>{@link com.epm.auth.domain.exception.InvitationTokenAlreadyUsedException} — token already used (409)</li>
 * </ul>
 */
public interface InvitationValidationPort {

    /**
     * Validates the invitation token against user-service.
     *
     * @param token plaintext base64url invitation token
     * @return validation result containing email, tenantId, teamId, role, and invitationId
     * @throws com.epm.auth.domain.exception.InvitationTokenInvalidException   if token not found
     * @throws com.epm.auth.domain.exception.InvitationTokenExpiredException   if token expired
     * @throws com.epm.auth.domain.exception.InvitationTokenAlreadyUsedException if token already used
     */
    InvitationValidationResult validateToken(String token);

    /**
     * Marks the invitation as used so it cannot be accepted again.
     *
     * @param invitationId UUID of the invitation to mark used
     */
    void markUsed(UUID invitationId);
}
