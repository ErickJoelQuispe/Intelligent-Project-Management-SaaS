package com.epm.user.application.usecase;

import java.util.UUID;

import com.epm.user.domain.exception.TeamNotFoundException;
import com.epm.user.domain.exception.UnauthorizedException;
import com.epm.user.domain.exception.UserNotFoundException;
import com.epm.user.domain.model.Team;
import com.epm.user.domain.model.TeamRole;
import com.epm.user.domain.port.in.AddTeamMemberUseCase;
import com.epm.user.domain.port.in.command.AddMemberCommand;
import com.epm.user.domain.port.out.DomainEventPublisher;
import com.epm.user.domain.port.out.TeamRepository;
import com.epm.user.domain.port.out.UserProfileRepository;

/**
 * Implementation of {@link AddTeamMemberUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class AddTeamMemberUseCaseImpl implements AddTeamMemberUseCase {

    private final TeamRepository teamRepository;
    private final UserProfileRepository profileRepository;
    private final DomainEventPublisher eventPublisher;

    public AddTeamMemberUseCaseImpl(TeamRepository teamRepository,
            UserProfileRepository profileRepository,
            DomainEventPublisher eventPublisher) {
        this.teamRepository = teamRepository;
        this.profileRepository = profileRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void addMember(UUID teamId, UUID callerId, UUID tenantId, AddMemberCommand command) {
        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new TeamNotFoundException(teamId));

        // Verify caller is OWNER
        boolean callerIsOwner = team.getMemberships().stream()
                .anyMatch(m -> m.getUserId().equals(callerId)
                        && m.getRole() == TeamRole.OWNER
                        && m.isActive());
        if (!callerIsOwner) {
            throw new UnauthorizedException("Only team owners can add members");
        }

        // Verify target user exists
        if (!profileRepository.existsByIdAndTenantId(command.userId(), tenantId)) {
            throw new UserNotFoundException(command.userId());
        }

        team.addMember(command.userId(), command.role());
        eventPublisher.publish(team.pullDomainEvents());
        teamRepository.save(team);
    }
}
