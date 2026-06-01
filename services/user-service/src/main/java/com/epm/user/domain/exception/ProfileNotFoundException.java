package com.epm.user.domain.exception;

import java.util.UUID;

/**
 * Thrown when a user profile is not found.
 */
public class ProfileNotFoundException extends RuntimeException {

    public ProfileNotFoundException(UUID userId) {
        super("Profile not found for user: " + userId);
    }
}
