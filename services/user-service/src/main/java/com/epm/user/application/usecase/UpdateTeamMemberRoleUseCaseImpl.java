package com.epm.user.application.usecase;

import java.util.UUID;

import com.epm.user.domain.exception.SelfRoleChangeException;
import com.epm.user.domain.exception.TeamNotFoundException;
import com.epm.user.domain.exception.UnauthorizedException;
import com.epm.user.domain.model.Team;
import com.epm.user.domain.model.TeamMembership;
import com.epm.user.domain.model.TeamRole;
import com.epm.user.domain.port.in.UpdateTeamMemberRoleUseCase;
import com.epm.user.domain.port.in.command.UpdateTeamMemberRoleCommand;
import com.epm.user.domain.port.in.result.TeamResult;
import com.epm.user.domain.port.out.TeamRepository;
import com.epm.user.domain.port.out.TransactionalOutboxWriter;
import com.epm.user.domain.port.out.UserProfileRepository;

/**
 * Implementation of {@link UpdateTeamMemberRoleUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class UpdateTeamMemberRoleUseCaseImpl implements UpdateTeamMemberRoleUseCase {

    private final TeamRepository teamRepository;
    private final UserProfileRepository profileRepository;
    private final TransactionalOutboxWriter outboxWriter;

    public UpdateTeamMemberRoleUseCaseImpl(TeamRepository teamRepository,
            UserProfileRepository profileRepository,
            TransactionalOutboxWriter outboxWriter) {
        this.teamRepository = teamRepository;
        this.profileRepository = profileRepository;
        this.outboxWriter = outboxWriter;
    }

    @Override
    public TeamResult execute(UpdateTeamMemberRoleCommand command) {
        UUID teamId = command.teamId();
        UUID tenantId = command.tenantId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new TeamNotFoundException(teamId));

        boolean callerIsOwner = team.getMemberships().stream()
                .anyMatch(m -> m.getUserId().equals(command.requesterId())
                        && m.getRole() == TeamRole.OWNER
                        && m.isActive());
        if (!callerIsOwner) {
            throw new UnauthorizedException("Only team owners can change member roles");
        }

        if (command.memberId().equals(command.requesterId())) {
            throw new SelfRoleChangeException();
        }

        TeamMembership target = team.getMemberships().stream()
                .filter(m -> m.getUserId().equals(command.memberId()) && m.isActive())
                .findFirst()
                .orElseThrow(() -> new TeamNotFoundException(teamId));

        target.changeRole(command.newRole());
        Team saved = outboxWriter.saveTeamAndPublish(team);

        return TeamResultMapper.toResult(saved, tenantId, profileRepository);
    }
}
