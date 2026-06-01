package com.epm.user.infrastructure.adapter.in.rest;

import java.util.UUID;

/**
 * Response body for user profile endpoints.
 */
public record UserProfileResponse(
        UUID id,
        UUID tenantId,
        String email,
        String firstName,
        String lastName,
        String bio,
        String avatarUrl,
        long version) {
}
