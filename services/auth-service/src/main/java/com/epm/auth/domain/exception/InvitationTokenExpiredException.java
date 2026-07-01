package com.epm.auth.domain.exception;

/**
 * Thrown when an invitation token has passed its expiry date.
 *
 * <p>Pure Java exception — no Spring, no HTTP status annotations.
 * The infrastructure layer maps this to HTTP 410 Gone.
 */
public class InvitationTokenExpiredException extends RuntimeException {

    public InvitationTokenExpiredException(String message) {
        super(message);
    }
}
