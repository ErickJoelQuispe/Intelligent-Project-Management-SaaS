package com.epm.user.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.epm.user.domain.exception.TeamNotFoundException;
import com.epm.user.domain.exception.UnauthorizedException;
import com.epm.user.domain.model.Team;
import com.epm.user.domain.model.TeamRole;
import com.epm.user.domain.port.in.command.UpdateTeamCommand;
import com.epm.user.domain.port.in.result.TeamResult;
import com.epm.user.domain.port.out.TeamRepository;
import com.epm.user.domain.port.out.TransactionalOutboxWriter;
import com.epm.user.domain.port.out.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link UpdateTeamUseCaseImpl}.
 */
@ExtendWith(MockitoExtension.class)
class UpdateTeamUseCaseImplTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private UserProfileRepository profileRepository;

    @Mock
    private TransactionalOutboxWriter outboxWriter;

    private UpdateTeamUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateTeamUseCaseImpl(teamRepository, profileRepository, outboxWriter);
    }

    @Test
    void ownerUpdatesTeam_returnsUpdatedTeamResult() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Team team = Team.create(tenantId, ownerId, "Old Name", "Old Desc");
        when(teamRepository.findByIdAndTenantId(team.getId(), tenantId)).thenReturn(Optional.of(team));
        when(outboxWriter.saveTeamAndPublish(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTeamCommand command = new UpdateTeamCommand(
                team.getId(), ownerId, tenantId, "New Name", "New Desc");
        TeamResult result = useCase.execute(command);

        assertThat(result.name()).isEqualTo("New Name");
        assertThat(result.id()).isEqualTo(team.getId());
        verify(outboxWriter).saveTeamAndPublish(any());
    }

    @Test
    void nonOwnerUpdatesTeam_throwsUnauthorizedException() {
        UUID ownerId = UUID.randomUUID();
        UUID nonOwnerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Team team = Team.create(tenantId, ownerId, "Old Name", null);
        team.addMember(nonOwnerId, TeamRole.MEMBER, "test@example.com");
        when(teamRepository.findByIdAndTenantId(team.getId(), tenantId)).thenReturn(Optional.of(team));

        UpdateTeamCommand command = new UpdateTeamCommand(
                team.getId(), nonOwnerId, tenantId, "New Name", null);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void teamNotFound_throwsTeamNotFoundException() {
        UUID teamId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(teamRepository.findByIdAndTenantId(teamId, tenantId)).thenReturn(Optional.empty());

        UpdateTeamCommand command = new UpdateTeamCommand(
                teamId, UUID.randomUUID(), tenantId, "New Name", null);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(TeamNotFoundException.class);
    }

    @Test
    void ownerUpdatesNameOnly_descriptionPreserved() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Team team = Team.create(tenantId, ownerId, "Old Name", "Keep This");
        when(teamRepository.findByIdAndTenantId(team.getId(), tenantId)).thenReturn(Optional.of(team));
        when(outboxWriter.saveTeamAndPublish(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTeamCommand command = new UpdateTeamCommand(
                team.getId(), ownerId, tenantId, "New Name", null);
        TeamResult result = useCase.execute(command);

        assertThat(result.name()).isEqualTo("New Name");
        assertThat(result.description()).isEqualTo("Keep This");
    }
}
