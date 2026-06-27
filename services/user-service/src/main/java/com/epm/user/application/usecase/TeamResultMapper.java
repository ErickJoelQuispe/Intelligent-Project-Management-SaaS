package com.epm.user.application.usecase;

import java.util.List;
import java.util.UUID;

import com.epm.user.domain.model.Team;
import com.epm.user.domain.model.UserProfile;
import com.epm.user.domain.port.in.result.MemberResult;
import com.epm.user.domain.port.in.result.TeamResult;
import com.epm.user.domain.port.out.UserProfileRepository;

/**
 * Package-private helper that converts a {@link Team} aggregate into an enriched {@link TeamResult}.
 *
 * <p>Extracted from {@code GetTeamUseCaseImpl} to avoid duplication across use cases
 * that return profile-enriched team results.
 */
class TeamResultMapper {

    private TeamResultMapper() {
        // utility class
    }

    /**
     * Converts a {@link Team} to a {@link TeamResult} with member profile enrichment.
     *
     * @param team             the team aggregate
     * @param tenantId         the tenant to use for profile lookup
     * @param profileRepository the repository to fetch member profiles from
     * @return an enriched {@link TeamResult}
     */
    static TeamResult toResult(Team team, UUID tenantId, UserProfileRepository profileRepository) {
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
