package com.epm.auth.infrastructure.adapter.in.rest;

import java.util.UUID;

/**
 * HTTP response DTO for POST /api/v1/auth/register.
 */
public record RegisterAccountResponse(
        UUID accountId,
        UUID keycloakUserId,
        String email) {
}
