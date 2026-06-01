package com.epm.auth.domain.exception;

/**
 * Thrown when the identity provider (Keycloak) is unavailable or returns an error.
 *
 * <p>Pure Java exception — no Spring, no HTTP status annotations.
 * The infrastructure layer maps this to HTTP 503 with Retry-After header.
 */
public class IdentityProviderException extends RuntimeException {

    private final int retryAfterSeconds;

    public IdentityProviderException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public IdentityProviderException(String message, Throwable cause) {
        super(message, cause);
        this.retryAfterSeconds = 30;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
