package com.epm.user.application.usecase;

import java.util.UUID;

import com.epm.user.domain.exception.TeamNotFoundException;
import com.epm.user.domain.exception.UnauthorizedException;
import com.epm.user.domain.model.Team;
import com.epm.user.domain.model.TeamRole;
import com.epm.user.domain.port.in.UpdateTeamUseCase;
import com.epm.user.domain.port.in.command.UpdateTeamCommand;
import com.epm.user.domain.port.in.result.TeamResult;
import com.epm.user.domain.port.out.TeamRepository;
import com.epm.user.domain.port.out.TransactionalOutboxWriter;
import com.epm.user.domain.port.out.UserProfileRepository;

/**
 * Implementation of {@link UpdateTeamUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class UpdateTeamUseCaseImpl implements UpdateTeamUseCase {

    private final TeamRepository teamRepository;
    private final UserProfileRepository profileRepository;
    private final TransactionalOutboxWriter outboxWriter;

    public UpdateTeamUseCaseImpl(TeamRepository teamRepository,
            UserProfileRepository profileRepository,
            TransactionalOutboxWriter outboxWriter) {
        this.teamRepository = teamRepository;
        this.profileRepository = profileRepository;
        this.outboxWriter = outboxWriter;
    }

    @Override
    public TeamResult execute(UpdateTeamCommand command) {
        Team team = teamRepository.findByIdAndTenantId(command.teamId(), command.tenantId())
                .orElseThrow(() -> new TeamNotFoundException(command.teamId()));

        boolean callerIsOwner = team.getMemberships().stream()
                .anyMatch(m -> m.getUserId().equals(command.requesterId())
                        && m.getRole() == TeamRole.OWNER
                        && m.isActive());
        if (!callerIsOwner) {
            throw new UnauthorizedException("Only team owners can update the team");
        }

        team.update(command.name(), command.description());
        Team saved = outboxWriter.saveTeamAndPublish(team);

        return TeamResultMapper.toResult(saved, command.tenantId(), profileRepository);
    }
}
