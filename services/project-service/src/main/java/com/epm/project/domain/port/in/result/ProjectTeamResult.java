package com.epm.project.domain.port.in.result;

import java.time.Instant;
import java.util.UUID;

/**
 * Read model for a team assignment within a project result.
 */
public record ProjectTeamResult(
        UUID id,
        UUID projectId,
        UUID teamId,
        Instant assignedAt) {
}
