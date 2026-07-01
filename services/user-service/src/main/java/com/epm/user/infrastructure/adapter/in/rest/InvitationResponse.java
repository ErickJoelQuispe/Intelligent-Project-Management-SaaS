package com.epm.user.infrastructure.adapter.in.rest;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body for {@code POST /api/v1/teams/{teamId}/invite}.
 */
public record InvitationResponse(
        UUID invitationId,
        String email,
        Instant expiresAt) {
}
