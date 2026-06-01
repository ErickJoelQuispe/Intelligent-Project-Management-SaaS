package com.epm.auth.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Email value object.
 *
 * <p>Immutable, self-validating, normalized to lowercase.
 * No Spring, no JPA — pure Java domain object.
 */
public record Email(String value) {

    private static final Pattern RFC_5322 =
            Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    public Email {
        if (value == null) {
            throw new IllegalArgumentException("email must not be null");
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (!RFC_5322.matcher(normalized).matches()) {
            throw new IllegalArgumentException("invalid email format: " + value);
        }
        value = normalized;
    }
}
