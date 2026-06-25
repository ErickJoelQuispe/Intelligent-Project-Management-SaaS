package com.epm.user.application.usecase;

import java.util.List;
import java.util.UUID;

import com.epm.user.domain.model.Team;
import com.epm.user.domain.port.in.ListTeamsUseCase;
import com.epm.user.domain.port.in.result.MemberResult;
import com.epm.user.domain.port.in.result.TeamResult;
import com.epm.user.domain.port.out.TeamRepository;

/**
 * Implementation of {@link ListTeamsUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class ListTeamsUseCaseImpl implements ListTeamsUseCase {

    private final TeamRepository teamRepository;

    public ListTeamsUseCaseImpl(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @Override
    public List<TeamResult> listTeams(UUID userId, UUID tenantId) {
        return teamRepository.findAllByMemberUserId(userId, tenantId).stream()
                .map(this::toResult)
                .toList();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private TeamResult toResult(Team team) {
        List<MemberResult> members = team.getMemberships().stream()
                .filter(m -> m.isActive())
                .map(m -> new MemberResult(m.getUserId(), m.getRole(), m.getJoinedAt(), null, null, null))
                .toList();
        return new TeamResult(team.getId(), team.getTenantId(), team.getOwnerId(),
                team.getName(), team.getDescription(), members);
    }
}
