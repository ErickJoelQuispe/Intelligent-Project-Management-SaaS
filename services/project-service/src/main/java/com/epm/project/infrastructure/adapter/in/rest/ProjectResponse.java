package com.epm.project.infrastructure.adapter.in.rest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response body for project operations.
 */
public record ProjectResponse(
        UUID id,
        String name,
        String description,
        String status,
        String visibility,
        UUID ownerProfileId,
        UUID tenantId,
        List<TeamAssignmentResponse> teams,
        List<ProjectMemberResponse> members,
        Instant createdAt,
        Instant updatedAt) {
}
