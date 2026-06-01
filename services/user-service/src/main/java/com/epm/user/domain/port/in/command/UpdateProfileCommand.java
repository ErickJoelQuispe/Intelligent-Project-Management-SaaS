package com.epm.user.domain.port.in.command;

/**
 * Command to update a user's profile.
 */
public record UpdateProfileCommand(
        String firstName,
        String lastName,
        String bio,
        String avatarUrl,
        long version) {
}
