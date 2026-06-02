package com.epm.project.domain.port.in.result;

import java.time.Instant;
import java.util.UUID;

/**
 * Read model for a member within a project result.
 */
public record ProjectMemberResult(
        UUID id,
        UUID projectId,
        UUID profileId,
        String role,
        Instant joinedAt) {
}
