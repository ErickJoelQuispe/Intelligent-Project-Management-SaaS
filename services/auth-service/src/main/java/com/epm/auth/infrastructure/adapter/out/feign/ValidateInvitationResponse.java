package com.epm.auth.infrastructure.adapter.out.feign;

import java.util.UUID;

/**
 * DTO that mirrors the response body returned by user-service
 * {@code GET /api/v1/invitations/validate?token=}.
 *
 * @param invitationId UUID of the invitation
 * @param email        email address the invitation was sent to
 * @param tenantId     UUID of the inviting tenant
 * @param teamId       UUID of the team
 * @param role         role to assign (e.g. "VIEWER")
 */
public record ValidateInvitationResponse(
        UUID invitationId,
        String email,
        UUID tenantId,
        UUID teamId,
        String role) {
}
