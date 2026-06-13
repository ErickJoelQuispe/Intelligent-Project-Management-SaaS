package com.epm.user.domain.exception;

/**
 * Thrown when a team name fails validation (blank or exceeds maximum length).
 */
public class InvalidTeamNameException extends RuntimeException {

    public InvalidTeamNameException(String message) {
        super(message);
    }
}
