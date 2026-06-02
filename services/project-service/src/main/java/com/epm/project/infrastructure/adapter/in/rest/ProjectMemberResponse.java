package com.epm.project.infrastructure.adapter.in.rest;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body for a member within a project.
 */
public record ProjectMemberResponse(UUID profileId, String role, Instant joinedAt) {
}
