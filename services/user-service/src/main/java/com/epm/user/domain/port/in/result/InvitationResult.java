package com.epm.user.domain.port.in.result;

import java.time.Instant;
import java.util.UUID;

/**
 * Result returned after creating an invitation.
 */
public record InvitationResult(
        UUID invitationId,
        String email,
        Instant expiresAt) {
}
