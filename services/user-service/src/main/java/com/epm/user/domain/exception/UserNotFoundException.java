package com.epm.user.domain.exception;

import java.util.UUID;

/**
 * Thrown when a target user is not found.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID userId) {
        super("User not found: " + userId);
    }
}
