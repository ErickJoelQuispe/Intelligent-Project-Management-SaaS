package com.epm.user.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.epm.user.domain.exception.LastOwnerException;
import com.epm.user.domain.exception.UnauthorizedException;
import com.epm.user.domain.model.Team;
import com.epm.user.domain.model.TeamRole;
import com.epm.user.domain.port.out.DomainEventPublisher;
import com.epm.user.domain.port.out.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link RemoveTeamMemberUseCaseImpl}.
 * RED: RemoveTeamMemberUseCaseImpl does not exist yet.
 */
@ExtendWith(MockitoExtension.class)
class RemoveTeamMemberUseCaseTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    private RemoveTeamMemberUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new RemoveTeamMemberUseCaseImpl(teamRepository, eventPublisher);
    }

    @Test
    void happyPathRemovesMemberAndPublishesEvent() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Team team = Team.create(tenantId, ownerId, "Alpha", null);
        team.addMember(memberId, TeamRole.MEMBER);
        team.pullDomainEvents();
        when(teamRepository.findByIdAndTenantId(team.getId(), tenantId)).thenReturn(Optional.of(team));
        when(teamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.removeMember(team.getId(), ownerId, memberId, tenantId);

        verify(teamRepository).save(any());
        verify(eventPublisher).publish(any());
    }

    @Test
    void callerNotOwnerThrowsUnauthorizedException() {
        UUID ownerId = UUID.randomUUID();
        UUID nonOwnerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Team team = Team.create(tenantId, ownerId, "Alpha", null);
        team.addMember(nonOwnerId, TeamRole.MEMBER);
        team.addMember(memberId, TeamRole.MEMBER);
        team.pullDomainEvents();
        when(teamRepository.findByIdAndTenantId(team.getId(), tenantId)).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> useCase.removeMember(team.getId(), nonOwnerId, memberId, tenantId))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void removingLastOwnerThrowsLastOwnerException() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Team team = Team.create(tenantId, ownerId, "Alpha", null);
        team.pullDomainEvents();
        when(teamRepository.findByIdAndTenantId(team.getId(), tenantId)).thenReturn(Optional.of(team));

        // Only one owner — removing it should throw
        assertThatThrownBy(() -> useCase.removeMember(team.getId(), ownerId, ownerId, tenantId))
                .isInstanceOf(LastOwnerException.class);
    }
}
