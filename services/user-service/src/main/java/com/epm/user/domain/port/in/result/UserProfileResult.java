package com.epm.user.domain.port.in.result;

import java.util.UUID;

import com.epm.user.domain.model.UserPreferences;

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
        boolean provisional,
        UserPreferences preferences) {

    /**
     * Convenience constructor without preferences (backward-compatible).
     */
    public UserProfileResult(UUID id, UUID tenantId, String email, String firstName,
            String lastName, String bio, String avatarUrl, long version, boolean provisional) {
        this(id, tenantId, email, firstName, lastName, bio, avatarUrl, version, provisional, null);
    }
}
