package com.epm.user.infrastructure.adapter.in.rest;

import java.util.UUID;

/**
 * Response body for {@code GET /api/v1/invitations/validate}.
 */
public record ValidateInvitationResponse(
        UUID invitationId,
        String email,
        UUID tenantId,
        UUID teamId,
        String role) {
}
