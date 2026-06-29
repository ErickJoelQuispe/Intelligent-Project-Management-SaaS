package com.epm.auth.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

/**
 * HTTP request DTO for POST /api/v1/auth/accept-invitation.
 *
 * <p>All fields are required. The password min-length requirement is not enforced here
 * because the invited user's password is set directly in Keycloak and the policy
 * is managed at the Keycloak realm level.
 *
 * @param token     base64url invitation token from the email link
 * @param firstName invited user's first name
 * @param lastName  invited user's last name
 * @param password  raw password chosen by the invited user
 */
public record AcceptInvitationRequest(
        @NotBlank(message = "token is required")
        String token,

        @NotBlank(message = "firstName is required")
        String firstName,

        @NotBlank(message = "lastName is required")
        String lastName,

        @NotBlank(message = "password is required")
        String password) {
}
