package com.epm.user.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/teams/{teamId}/invite}.
 */
public record InviteRequest(
        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be a valid email address")
        String email) {
}
