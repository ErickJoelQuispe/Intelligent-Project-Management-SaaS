package com.epm.user.domain.exception;

/**
 * Thrown when a {@code UserPreferences} value object fails validation.
 */
public class InvalidPreferencesException extends RuntimeException {

    public InvalidPreferencesException(String field, String value) {
        super("Invalid value for field '" + field + "': " + value);
    }
}
