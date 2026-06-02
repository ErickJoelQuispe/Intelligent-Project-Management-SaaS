package com.epm.project.domain.port.in.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read model returned by project use cases.
 */
public record ProjectResult(
        UUID id,
        String name,
        String description,
        String status,
        String visibility,
        UUID ownerProfileId,
        UUID tenantId,
        List<ProjectTeamResult> teams,
        List<ProjectMemberResult> members,
        Instant createdAt,
        Instant updatedAt) {
}
