package com.epm.user.domain.port.in.command;

import java.util.UUID;

/**
 * Command to create a new team invitation.
 */
public record CreateInvitationCommand(
        UUID teamId,
        UUID tenantId,
        String email,
        String createdBy) {
}
