package com.epm.user.domain.exception;

/**
 * Thrown when attempting to remove the last owner of a team.
 */
public class LastOwnerException extends RuntimeException {

    public LastOwnerException() {
        super("Cannot remove the last owner of a team");
    }
}
