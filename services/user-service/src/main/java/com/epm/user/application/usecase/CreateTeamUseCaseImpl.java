package com.epm.user.application.usecase;

import java.util.List;
import java.util.UUID;

import com.epm.user.domain.exception.InvalidTeamNameException;
import com.epm.user.domain.model.Team;
import com.epm.user.domain.port.in.CreateTeamUseCase;
import com.epm.user.domain.port.in.command.CreateTeamCommand;
import com.epm.user.domain.port.in.result.MemberResult;
import com.epm.user.domain.port.in.result.TeamResult;
import com.epm.user.domain.port.out.TransactionalOutboxWriter;

/**
 * Implementation of {@link CreateTeamUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class CreateTeamUseCaseImpl implements CreateTeamUseCase {

    private final TransactionalOutboxWriter outboxWriter;

    public CreateTeamUseCaseImpl(TransactionalOutboxWriter outboxWriter) {
        this.outboxWriter = outboxWriter;
    }

    @Override
    public TeamResult createTeam(UUID ownerId, UUID tenantId, CreateTeamCommand command) {
        if (command.name() == null || command.name().isBlank()) {
            throw new InvalidTeamNameException("Team name must not be blank");
        }
        if (command.name().length() > 100) {
            throw new InvalidTeamNameException("Team name must not exceed 100 characters");
        }

        Team team = Team.create(tenantId, ownerId, command.name(), command.description());
        Team saved = outboxWriter.saveTeamAndPublish(team);

        return toResult(saved);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private TeamResult toResult(Team team) {
        List<MemberResult> members = team.getMemberships().stream()
                .filter(m -> m.isActive())
                .map(m -> new MemberResult(m.getUserId(), m.getRole(), m.getJoinedAt()))
                .toList();
        return new TeamResult(team.getId(), team.getTenantId(), team.getOwnerId(),
                team.getName(), team.getDescription(), members);
    }
}
