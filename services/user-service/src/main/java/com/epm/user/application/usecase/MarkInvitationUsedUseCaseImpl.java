package com.epm.user.application.usecase;

import java.util.UUID;

import com.epm.user.domain.exception.InvitationNotFoundException;
import com.epm.user.domain.model.Invitation;
import com.epm.user.domain.port.in.MarkInvitationUsedUseCase;
import com.epm.user.domain.port.out.InvitationRepository;

/**
 * Implementation of {@link MarkInvitationUsedUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class MarkInvitationUsedUseCaseImpl implements MarkInvitationUsedUseCase {

    private final InvitationRepository invitationRepository;

    public MarkInvitationUsedUseCaseImpl(InvitationRepository invitationRepository) {
        this.invitationRepository = invitationRepository;
    }

    @Override
    public void markInvitationUsed(UUID invitationId) {
        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new InvitationNotFoundException(
                        "Invitation " + invitationId + " not found"));

        invitation.markUsed();
        invitationRepository.save(invitation);
    }
}
