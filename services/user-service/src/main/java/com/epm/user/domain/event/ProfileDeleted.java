package com.epm.user.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a user profile is soft-deleted.
 */
public record ProfileDeleted(UUID userId, Instant deletedAt) {
}
