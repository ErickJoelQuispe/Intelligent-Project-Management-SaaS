package com.epm.user.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a new invitation is created.
 *
 * <p>Contains the plaintext token (not the hash) so that downstream consumers
 * (e.g. notification-service) can embed the token in the invitation email link.
 */
public record InvitationCreatedEvent(
        UUID eventId,
        UUID invitationId,
        UUID teamId,
        UUID tenantId,
        String email,
        String token,
        String role,
        Instant expiresAt,
        Instant occurredAt) {
}
