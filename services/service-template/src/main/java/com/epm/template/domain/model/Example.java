package com.epm.template.domain.model;

import java.util.UUID;

/**
 * Example aggregate root.
 *
 * <p>Domain objects are plain Java — no Spring, no JPA annotations here.
 * Infrastructure concerns (persistence, serialization) live in the adapters.
 */
public record Example(UUID id, String name) {

    public Example {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
