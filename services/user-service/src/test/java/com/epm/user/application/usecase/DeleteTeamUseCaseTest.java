package com.epm.user.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.epm.user.domain.exception.TeamNotFoundException;
import com.epm.user.domain.exception.UnauthorizedException;
import com.epm.user.domain.model.Team;
import com.epm.user.domain.port.out.TeamRepository;
import com.epm.user.domain.port.out.TransactionalOutboxWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link DeleteTeamUseCaseImpl}.
 */
@ExtendWith(MockitoExtension.class)
class DeleteTeamUseCaseTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TransactionalOutboxWriter outboxWriter;

    private DeleteTeamUseCaseImpl useCase;

    private UUID tenantId;
    private UUID ownerId;
    private UUID teamId;

    @BeforeEach
    void setUp() {
        useCase = new DeleteTeamUseCaseImpl(teamRepository, outboxWriter);
        tenantId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        teamId = UUID.randomUUID();
    }

    @Test
    void deleteTeam_happyPath_deletesAndPublishesEvent() {
        Team team = Team.create(tenantId, ownerId, "Alpha Team", null);
        when(teamRepository.findByIdAndTenantId(teamId, tenantId)).thenReturn(Optional.of(team));
        when(outboxWriter.saveTeamAndPublish(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.deleteTeam(teamId, ownerId, tenantId);

        verify(outboxWriter).saveTeamAndPublish(any());
    }

    @Test
    void deleteTeam_teamNotFound_throwsTeamNotFoundException() {
        when(teamRepository.findByIdAndTenantId(teamId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.deleteTeam(teamId, ownerId, tenantId))
                .isInstanceOf(TeamNotFoundException.class);
    }

    @Test
    void deleteTeam_callerNotOwner_throwsUnauthorizedException() {
        UUID nonOwnerId = UUID.randomUUID();
        Team team = Team.create(tenantId, ownerId, "Alpha Team", null);
        when(teamRepository.findByIdAndTenantId(teamId, tenantId)).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> useCase.deleteTeam(teamId, nonOwnerId, tenantId))
                .isInstanceOf(UnauthorizedException.class);
    }
}
