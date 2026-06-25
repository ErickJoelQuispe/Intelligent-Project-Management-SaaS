package com.epm.user.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.epm.user.domain.exception.TeamNotFoundException;
import com.epm.user.domain.model.Team;
import com.epm.user.domain.port.in.result.TeamResult;
import com.epm.user.domain.port.out.TeamRepository;
import com.epm.user.domain.port.out.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link GetTeamUseCaseImpl}.
 * RED: GetTeamUseCaseImpl does not exist yet.
 */
@ExtendWith(MockitoExtension.class)
class GetTeamUseCaseTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private UserProfileRepository profileRepository;

    private GetTeamUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetTeamUseCaseImpl(teamRepository, profileRepository);
    }

    @Test
    void teamFoundAndCallerIsMemberReturnsTeamResult() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Team team = Team.create(tenantId, ownerId, "Alpha", null);
        when(teamRepository.findByIdAndTenantId(team.getId(), tenantId)).thenReturn(Optional.of(team));

        TeamResult result = useCase.getTeam(team.getId(), ownerId, tenantId);

        assertThat(result.id()).isEqualTo(team.getId());
        assertThat(result.name()).isEqualTo("Alpha");
    }

    @Test
    void teamNotFoundThrowsTeamNotFoundException() {
        UUID teamId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(teamRepository.findByIdAndTenantId(teamId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getTeam(teamId, UUID.randomUUID(), tenantId))
                .isInstanceOf(TeamNotFoundException.class);
    }

    @Test
    void callerNotMemberThrowsTeamNotFoundException() {
        UUID ownerId = UUID.randomUUID();
        UUID nonMemberId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Team team = Team.create(tenantId, ownerId, "Alpha", null);
        when(teamRepository.findByIdAndTenantId(team.getId(), tenantId)).thenReturn(Optional.of(team));

        // nonMemberId is not a member of this team
        assertThatThrownBy(() -> useCase.getTeam(team.getId(), nonMemberId, tenantId))
                .isInstanceOf(TeamNotFoundException.class);
    }
}
