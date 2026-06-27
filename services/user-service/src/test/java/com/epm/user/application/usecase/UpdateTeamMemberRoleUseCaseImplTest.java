package com.epm.user.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.epm.user.domain.exception.SelfRoleChangeException;
import com.epm.user.domain.exception.TeamNotFoundException;
import com.epm.user.domain.exception.UnauthorizedException;
import com.epm.user.domain.model.Team;
import com.epm.user.domain.model.TeamRole;
import com.epm.user.domain.port.in.command.UpdateTeamMemberRoleCommand;
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
 * Unit tests for {@link UpdateTeamMemberRoleUseCaseImpl}.
 */
@ExtendWith(MockitoExtension.class)
class UpdateTeamMemberRoleUseCaseImplTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private UserProfileRepository profileRepository;

    @Mock
    private TransactionalOutboxWriter outboxWriter;

    private UpdateTeamMemberRoleUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateTeamMemberRoleUseCaseImpl(teamRepository, profileRepository, outboxWriter);
    }

    @Test
    void ownerChangesMemberRole_returnsUpdatedTeamResult() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Team team = Team.create(tenantId, ownerId, "Alpha", null);
        team.addMember(memberId, TeamRole.MEMBER);
        when(teamRepository.findByIdAndTenantId(team.getId(), tenantId)).thenReturn(Optional.of(team));
        when(outboxWriter.saveTeamAndPublish(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTeamMemberRoleCommand command = new UpdateTeamMemberRoleCommand(
                team.getId(), memberId, ownerId, tenantId, TeamRole.VIEWER);
        TeamResult result = useCase.execute(command);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(team.getId());
    }

    @Test
    void nonOwnerAttemptsRoleChange_throwsUnauthorizedException() {
        UUID ownerId = UUID.randomUUID();
        UUID nonOwnerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Team team = Team.create(tenantId, ownerId, "Alpha", null);
        team.addMember(nonOwnerId, TeamRole.MEMBER);
        team.addMember(memberId, TeamRole.VIEWER);
        when(teamRepository.findByIdAndTenantId(team.getId(), tenantId)).thenReturn(Optional.of(team));

        UpdateTeamMemberRoleCommand command = new UpdateTeamMemberRoleCommand(
                team.getId(), memberId, nonOwnerId, tenantId, TeamRole.MEMBER);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void ownerAttemptsToChangeSelfRole_throwsSelfRoleChangeException() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Team team = Team.create(tenantId, ownerId, "Alpha", null);
        when(teamRepository.findByIdAndTenantId(team.getId(), tenantId)).thenReturn(Optional.of(team));

        UpdateTeamMemberRoleCommand command = new UpdateTeamMemberRoleCommand(
                team.getId(), ownerId, ownerId, tenantId, TeamRole.MEMBER);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(SelfRoleChangeException.class);
    }

    @Test
    void memberNotFound_throwsTeamNotFoundException() {
        UUID ownerId = UUID.randomUUID();
        UUID nonMemberId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Team team = Team.create(tenantId, ownerId, "Alpha", null);
        when(teamRepository.findByIdAndTenantId(team.getId(), tenantId)).thenReturn(Optional.of(team));

        UpdateTeamMemberRoleCommand command = new UpdateTeamMemberRoleCommand(
                team.getId(), nonMemberId, ownerId, tenantId, TeamRole.VIEWER);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(TeamNotFoundException.class);
    }

    @Test
    void teamNotFound_throwsTeamNotFoundException() {
        UUID teamId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(teamRepository.findByIdAndTenantId(teamId, tenantId)).thenReturn(Optional.empty());

        UpdateTeamMemberRoleCommand command = new UpdateTeamMemberRoleCommand(
                teamId, UUID.randomUUID(), UUID.randomUUID(), tenantId, TeamRole.VIEWER);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(TeamNotFoundException.class);
    }
}
