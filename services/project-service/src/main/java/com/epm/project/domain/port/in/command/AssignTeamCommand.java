package com.epm.project.domain.port.in.command;

import java.util.UUID;

/**
 * Command to assign a team to a project.
 */
public record AssignTeamCommand(
        UUID projectId,
        UUID teamId,
        UUID callerProfileId,
        UUID tenantId) {
}
