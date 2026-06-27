package com.epm.user.domain.exception;

/**
 * Thrown when the team owner attempts to change their own role.
 */
public class SelfRoleChangeException extends RuntimeException {

    public SelfRoleChangeException() {
        super("Owner cannot change their own role");
    }
}
