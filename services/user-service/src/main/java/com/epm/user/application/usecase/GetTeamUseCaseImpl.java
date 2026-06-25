package com.epm.user.application.usecase;

import java.util.List;
import java.util.UUID;

import com.epm.user.domain.exception.TeamNotFoundException;
import com.epm.user.domain.model.Team;
import com.epm.user.domain.model.UserProfile;
import com.epm.user.domain.port.in.GetTeamUseCase;
import com.epm.user.domain.port.in.result.MemberResult;
import com.epm.user.domain.port.in.result.TeamResult;
import com.epm.user.domain.port.out.TeamRepository;
import com.epm.user.domain.port.out.UserProfileRepository;

/**
 * Implementation of {@link GetTeamUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 * Returns TeamNotFoundException whether team not found OR caller is not a member
 * (no information leakage per spec).
 */
public class GetTeamUseCaseImpl implements GetTeamUseCase {

    private final TeamRepository teamRepository;
    private final UserProfileRepository profileRepository;

    public GetTeamUseCaseImpl(TeamRepository teamRepository,
                               UserProfileRepository profileRepository) {
        this.teamRepository = teamRepository;
        this.profileRepository = profileRepository;
    }

    @Override
    public TeamResult getTeam(UUID teamId, UUID userId, UUID tenantId) {
        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new TeamNotFoundException(teamId));

        // Verify caller is a member (same exception — no info leakage)
        boolean isMember = team.getMemberships().stream()
                .anyMatch(m -> m.getUserId().equals(userId) && m.isActive());
        if (!isMember) {
            throw new TeamNotFoundException(teamId);
        }

        return toResult(team, tenantId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private TeamResult toResult(Team team, UUID tenantId) {
        List<MemberResult> members = team.getMemberships().stream()
                .filter(m -> m.isActive())
                .map(m -> {
                    UserProfile profile = profileRepository
                            .findByIdAndTenantId(m.getUserId(), tenantId)
                            .orElse(null);
                    String firstName = profile != null ? profile.getFirstName() : null;
                    String lastName  = profile != null ? profile.getLastName()  : null;
                    String email     = profile != null ? profile.getEmail()     : null;
                    return new MemberResult(m.getUserId(), m.getRole(), m.getJoinedAt(),
                            firstName, lastName, email);
                })
                .toList();
        return new TeamResult(team.getId(), team.getTenantId(), team.getOwnerId(),
                team.getName(), team.getDescription(), members);
    }
}
