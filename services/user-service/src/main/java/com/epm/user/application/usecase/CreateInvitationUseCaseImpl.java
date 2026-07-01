package com.epm.user.application.usecase;

import java.util.List;
import java.util.UUID;

import com.epm.user.domain.exception.ActiveInvitationExistsException;
import com.epm.user.domain.model.Invitation;
import com.epm.user.domain.port.in.CreateInvitationUseCase;
import com.epm.user.domain.port.in.command.CreateInvitationCommand;
import com.epm.user.domain.port.in.result.InvitationResult;
import com.epm.user.domain.port.out.DomainEventPublisher;
import com.epm.user.domain.port.out.InvitationRepository;

/**
 * Implementation of {@link CreateInvitationUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class CreateInvitationUseCaseImpl implements CreateInvitationUseCase {

    private final InvitationRepository invitationRepository;
    private final DomainEventPublisher domainEventPublisher;

    public CreateInvitationUseCaseImpl(InvitationRepository invitationRepository,
            DomainEventPublisher domainEventPublisher) {
        this.invitationRepository = invitationRepository;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Override
    public InvitationResult createInvitation(UUID callerId, CreateInvitationCommand command) {
        if (invitationRepository.existsActiveInvitation(command.email(), command.tenantId())) {
            throw new ActiveInvitationExistsException(command.email(), command.tenantId());
        }

        Invitation invitation = Invitation.create(command);
        List<Object> events = invitation.pullDomainEvents();
        Invitation saved = invitationRepository.save(invitation);
        domainEventPublisher.publish(events);

        return new InvitationResult(saved.getId(), saved.getEmail(), saved.getExpiresAt());
    }
}
