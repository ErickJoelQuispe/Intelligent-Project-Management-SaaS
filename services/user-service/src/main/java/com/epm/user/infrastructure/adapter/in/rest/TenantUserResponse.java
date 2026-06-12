package com.epm.user.infrastructure.adapter.in.rest;

import java.util.UUID;

/**
 * Read-only projection of a tenant user, returned by GET /api/v1/users.
 */
public record TenantUserResponse(UUID id, String email, String firstName, String lastName) {}
