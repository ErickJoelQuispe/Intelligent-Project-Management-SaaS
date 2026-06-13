package com.epm.user.application.usecase;

import java.util.UUID;

import com.epm.user.domain.exception.TeamNotFoundException;
import com.epm.user.domain.exception.UnauthorizedException;
import com.epm.user.domain.model.Team;
import com.epm.user.domain.model.TeamRole;
import com.epm.user.domain.port.in.RemoveTeamMemberUseCase;
import com.epm.user.domain.port.out.TeamRepository;
import com.epm.user.domain.port.out.TransactionalOutboxWriter;

/**
 * Implementation of {@link RemoveTeamMemberUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class RemoveTeamMemberUseCaseImpl implements RemoveTeamMemberUseCase {

    private final TeamRepository teamRepository;
    private final TransactionalOutboxWriter outboxWriter;

    public RemoveTeamMemberUseCaseImpl(TeamRepository teamRepository,
            TransactionalOutboxWriter outboxWriter) {
        this.teamRepository = teamRepository;
        this.outboxWriter = outboxWriter;
    }

    @Override
    public void removeMember(UUID teamId, UUID callerId, UUID targetUserId, UUID tenantId) {
        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new TeamNotFoundException(teamId));

        // Verify caller is OWNER
        boolean callerIsOwner = team.getMemberships().stream()
                .anyMatch(m -> m.getUserId().equals(callerId)
                        && m.getRole() == TeamRole.OWNER
                        && m.isActive());
        if (!callerIsOwner) {
            throw new UnauthorizedException("Only team owners can remove members");
        }

        team.removeMember(targetUserId);
        outboxWriter.saveTeamAndPublish(team);
    }
}
