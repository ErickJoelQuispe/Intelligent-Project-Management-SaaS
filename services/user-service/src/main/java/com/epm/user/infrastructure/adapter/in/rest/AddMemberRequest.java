package com.epm.user.infrastructure.adapter.in.rest;

import java.util.UUID;

import com.epm.user.domain.model.TeamRole;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /api/v1/teams/{teamId}/members.
 */
public record AddMemberRequest(@NotNull UUID userId, @NotNull TeamRole role) {
}
