package com.epm.user.domain.exception;

import java.util.UUID;

/**
 * Thrown when a user is already an active member of the team.
 */
public class DuplicateMemberException extends RuntimeException {

    public DuplicateMemberException(UUID userId) {
        super("User " + userId + " is already an active member of this team");
    }
}
