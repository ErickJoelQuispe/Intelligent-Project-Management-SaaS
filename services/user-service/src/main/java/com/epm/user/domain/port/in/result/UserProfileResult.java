package com.epm.user.domain.port.in.result;

import java.util.UUID;

/**
 * Result of a user profile query or update.
 *
 * <p>The {@code provisional} flag indicates the profile was built from JWT claims
 * (not yet persisted). Controllers should set {@code X-Profile-Source} header accordingly.
 */
public record UserProfileResult(
        UUID id,
        UUID tenantId,
        String email,
        String firstName,
        String lastName,
        String bio,
        String avatarUrl,
        long version,
        boolean provisional) {
}
