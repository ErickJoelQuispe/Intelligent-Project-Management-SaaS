package com.epm.user.domain.port.in.command;

import com.epm.user.domain.model.UserPreferences;

/**
 * Command to update a user's profile.
 */
public record UpdateProfileCommand(
        String firstName,
        String lastName,
        String bio,
        String avatarUrl,
        long version,
        UserPreferences preferences) {

    /**
     * Convenience constructor without preferences (backward-compatible).
     */
    public UpdateProfileCommand(String firstName, String lastName, String bio,
            String avatarUrl, long version) {
        this(firstName, lastName, bio, avatarUrl, version, null);
    }
}
