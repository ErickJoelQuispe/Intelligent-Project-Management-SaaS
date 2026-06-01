package com.epm.user.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for PATCH /api/v1/users/me.
 */
public record UpdateProfileRequest(
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 2000) String bio,
        @Size(max = 500) String avatarUrl,
        @NotNull Long version) {
}
