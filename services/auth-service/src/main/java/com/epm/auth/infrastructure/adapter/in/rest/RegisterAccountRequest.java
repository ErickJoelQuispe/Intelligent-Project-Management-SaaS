package com.epm.auth.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * HTTP request DTO for POST /api/v1/auth/register.
 *
 * <p>Bean Validation constraints enforce the spec requirements:
 * - email must be a valid email address
 * - password must be at least 8 characters
 * - firstName and lastName must be present and non-blank
 */
public record RegisterAccountRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, message = "password must be at least 8 characters")
        String password,

        @NotBlank(message = "firstName is required")
        String firstName,

        @NotBlank(message = "lastName is required")
        String lastName) {
}
