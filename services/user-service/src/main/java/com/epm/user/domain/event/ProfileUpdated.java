package com.epm.user.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a user profile is updated.
 */
public record ProfileUpdated(
        UUID eventId,
        UUID userId,
        UUID tenantId,
        String email,
        String firstName,
        String lastName,
        String bio,
        String avatarUrl,
        Instant occurredAt) {
}
