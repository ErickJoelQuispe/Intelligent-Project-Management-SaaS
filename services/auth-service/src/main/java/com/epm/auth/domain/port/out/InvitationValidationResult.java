package com.epm.auth.domain.port.out;

import java.util.UUID;

/**
 * Result returned by {@link InvitationValidationPort#validateToken(String)}.
 *
 * <p>Carries everything the {@code AcceptInvitationUseCase} needs to create the Keycloak user:
 * the invitation's UUID (for mark-used), the target email, the inviting tenant, the team,
 * and the role to assign.
 *
 * @param invitationId UUID of the validated invitation
 * @param email        email address the invitation was sent to
 * @param tenantId     UUID of the inviting tenant — MUST be used for Keycloak attribute; never regenerated
 * @param teamId       UUID of the team the user is being added to
 * @param role         role to assign in Keycloak (e.g. "VIEWER")
 */
public record InvitationValidationResult(
        UUID invitationId,
        String email,
        UUID tenantId,
        UUID teamId,
        String role) {
}
