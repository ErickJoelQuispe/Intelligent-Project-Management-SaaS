package com.epm.user.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * Request body for PATCH /api/v1/teams/{teamId}.
 *
 * <p>At least one of {@code name} or {@code description} must be non-null.
 * A non-null {@code name} must not be blank.
 */
public record UpdateTeamRequest(
        @Size(max = 100) String name,
        @Size(max = 500) String description) {

    /**
     * At least one field must be provided.
     */
    @AssertTrue(message = "At least one of name or description must be provided")
    public boolean isAtLeastOneFieldPresent() {
        return name != null || description != null;
    }

    /**
     * If name is provided it must not be blank.
     */
    @AssertTrue(message = "Team name must not be blank")
    public boolean isNameNotBlank() {
        return name == null || !name.isBlank();
    }
}
