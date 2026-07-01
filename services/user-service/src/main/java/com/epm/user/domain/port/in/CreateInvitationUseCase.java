package com.epm.user.domain.port.in;

import java.util.UUID;

import com.epm.user.domain.port.in.command.CreateInvitationCommand;
import com.epm.user.domain.port.in.result.InvitationResult;

/**
 * Driving port: creates a new team invitation.
 */
public interface CreateInvitationUseCase {

    /**
     * Creates and persists a new invitation for the given email.
     *
     * @param callerId the authenticated user requesting the invite (must be ADMIN)
     * @param command  invitation parameters
     * @return the created invitation result
     * @throws com.epm.user.domain.exception.ActiveInvitationExistsException if
     *     an active invitation already exists for the same email in the tenant
     */
    InvitationResult createInvitation(UUID callerId, CreateInvitationCommand command);
}
