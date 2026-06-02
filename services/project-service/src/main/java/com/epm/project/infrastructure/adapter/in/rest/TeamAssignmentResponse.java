package com.epm.project.infrastructure.adapter.in.rest;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body for a team assignment within a project.
 */
public record TeamAssignmentResponse(UUID teamId, Instant assignedAt) {
}
