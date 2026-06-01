package com.epm.user.domain.exception;

import java.util.UUID;

/**
 * Thrown when a team is not found (or caller is not a member — no info leakage).
 */
public class TeamNotFoundException extends RuntimeException {

    public TeamNotFoundException(UUID teamId) {
        super("Team not found: " + teamId);
    }
}
