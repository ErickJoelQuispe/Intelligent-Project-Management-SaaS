package com.epm.user.infrastructure.adapter.in.rest;

import java.time.Instant;
import java.util.UUID;

import com.epm.user.domain.model.TeamRole;

/**
 * Team member response DTO.
 */
public record MemberResponse(UUID userId, TeamRole role, Instant joinedAt) {
}
