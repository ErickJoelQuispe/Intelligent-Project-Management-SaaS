package com.epm.auth.domain.exception;

/**
 * Thrown when attempting to register an account with an email that already exists.
 *
 * <p>Pure Java exception — no Spring, no HTTP status annotations.
 * The infrastructure layer maps this to HTTP 409.
 */
public class DuplicateEmailException extends RuntimeException {

    private final String email;

    public DuplicateEmailException(String email) {
        super("Account with email already exists: " + email);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
