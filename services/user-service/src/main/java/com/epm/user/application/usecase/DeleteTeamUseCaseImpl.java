package com.epm.user.application.usecase;

import java.util.UUID;

import com.epm.user.domain.exception.TeamNotFoundException;
import com.epm.user.domain.exception.UnauthorizedException;
import com.epm.user.domain.model.Team;
import com.epm.user.domain.model.TeamRole;
import com.epm.user.domain.port.in.DeleteTeamUseCase;
import com.epm.user.domain.port.out.TeamRepository;
import com.epm.user.domain.port.out.TransactionalOutboxWriter;

/**
 * Implementation of {@link DeleteTeamUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class DeleteTeamUseCaseImpl implements DeleteTeamUseCase {

    private final TeamRepository teamRepository;
    private final TransactionalOutboxWriter outboxWriter;

    public DeleteTeamUseCaseImpl(TeamRepository teamRepository,
            TransactionalOutboxWriter outboxWriter) {
        this.teamRepository = teamRepository;
        this.outboxWriter = outboxWriter;
    }

    @Override
    public void deleteTeam(UUID teamId, UUID callerId, UUID tenantId) {
        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new TeamNotFoundException(teamId));

        boolean callerIsOwner = team.getMemberships().stream()
                .anyMatch(m -> m.getUserId().equals(callerId)
                        && m.getRole() == TeamRole.OWNER
                        && m.isActive());
        if (!callerIsOwner) {
            throw new UnauthorizedException("Only team owners can delete a team");
        }

        team.delete();
        outboxWriter.saveTeamAndPublish(team);
    }
}
