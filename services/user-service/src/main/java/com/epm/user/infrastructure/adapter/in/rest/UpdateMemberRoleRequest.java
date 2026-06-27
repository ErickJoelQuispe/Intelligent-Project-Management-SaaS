package com.epm.user.infrastructure.adapter.in.rest;

import com.epm.user.domain.model.TeamRole;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for PATCH /api/v1/teams/{teamId}/members/{userId}.
 */
public record UpdateMemberRoleRequest(@NotNull TeamRole role) {
}
