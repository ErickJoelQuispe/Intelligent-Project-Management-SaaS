package com.epm.user.domain.port.in;

import java.util.UUID;

/**
 * Driving port: marks an invitation as used after successful account creation.
 */
public interface MarkInvitationUsedUseCase {

    /**
     * Marks the invitation as used (sets usedAt to now).
     *
     * @param invitationId the invitation to mark
     * @throws com.epm.user.domain.exception.InvitationNotFoundException     if not found
     * @throws com.epm.user.domain.exception.InvitationAlreadyUsedException  if already used
     */
    void markInvitationUsed(UUID invitationId);
}
