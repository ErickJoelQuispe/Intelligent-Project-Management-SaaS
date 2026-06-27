package com.epm.user.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.epm.user.domain.exception.DuplicateMemberException;
import com.epm.user.domain.exception.TeamNotFoundException;
import com.epm.user.domain.exception.UnauthorizedException;
import com.epm.user.domain.exception.UserNotFoundException;
import com.epm.user.domain.model.Team;
import com.epm.user.domain.model.TeamRole;
import com.epm.user.domain.model.UserProfile;
import com.epm.user.domain.port.in.command.AddMemberCommand;
import com.epm.user.domain.port.out.TeamRepository;
import com.epm.user.domain.port.out.TransactionalOutboxWriter;
import com.epm.user.domain.port.out.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AddTeamMemberUseCaseImpl}.
 */
@ExtendWith(MockitoExtension.class)
class AddTeamMemberUseCaseTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private UserProfileRepository profileRepository;

    @Mock
    private TransactionalOutboxWriter outboxWriter;

    private AddTeamMemberUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new AddTeamMemberUseCaseImpl(teamRepository, profileRepository, outboxWriter);
    }

    @Test
    void happyPathAddsMemberAndPublishesEvent() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Team team = Team.create(tenantId, ownerId, "Alpha", null);
        UserProfile memberProfile = UserProfile.reconstitute(memberId, tenantId, "member@example.com",
                "Member", "User", null, null, 0, null, null, null, null);
        when(teamRepository.findByIdAndTenantId(team.getId(), tenantId)).thenReturn(Optional.of(team));
        when(profileRepository.findByIdAndTenantId(memberId, tenantId)).thenReturn(Optional.of(memberProfile));
        when(outboxWriter.saveTeamAndPublish(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.addMember(team.getId(), ownerId, tenantId, new AddMemberCommand(memberId, TeamRole.MEMBER));

        verify(outboxWriter).saveTeamAndPublish(any());
    }

    @Test
    void callerNotOwnerThrowsUnauthorizedException() {
        UUID ownerId = UUID.randomUUID();
        UUID nonOwnerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Team team = Team.create(tenantId, ownerId, "Alpha", null);
        // Add nonOwner as MEMBER first
        team.addMember(nonOwnerId, TeamRole.MEMBER, "test@example.com");
        team.pullDomainEvents();
        when(teamRepository.findByIdAndTenantId(team.getId(), tenantId)).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> useCase.addMember(team.getId(), nonOwnerId, tenantId,
                new AddMemberCommand(memberId, TeamRole.MEMBER)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void targetUserNotFoundThrowsUserNotFoundException() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Team team = Team.create(tenantId, ownerId, "Alpha", null);
        when(teamRepository.findByIdAndTenantId(team.getId(), tenantId)).thenReturn(Optional.of(team));
        when(profileRepository.findByIdAndTenantId(memberId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.addMember(team.getId(), ownerId, tenantId,
                new AddMemberCommand(memberId, TeamRole.MEMBER)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void duplicateMembershipThrowsDuplicateMemberException() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Team team = Team.create(tenantId, ownerId, "Alpha", null);
        team.addMember(memberId, TeamRole.MEMBER, "test@example.com");
        team.pullDomainEvents();
        UserProfile memberProfile = UserProfile.reconstitute(memberId, tenantId, "member@example.com",
                "Member", "User", null, null, 0, null, null, null, null);
        when(teamRepository.findByIdAndTenantId(team.getId(), tenantId)).thenReturn(Optional.of(team));
        when(profileRepository.findByIdAndTenantId(memberId, tenantId)).thenReturn(Optional.of(memberProfile));

        assertThatThrownBy(() -> useCase.addMember(team.getId(), ownerId, tenantId,
                new AddMemberCommand(memberId, TeamRole.VIEWER)))
                .isInstanceOf(DuplicateMemberException.class);
    }
}
