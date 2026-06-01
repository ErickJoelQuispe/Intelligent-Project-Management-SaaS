package com.epm.user.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.epm.user.domain.model.TeamRole;
import com.epm.user.domain.port.in.command.CreateTeamCommand;
import com.epm.user.domain.port.in.result.TeamResult;
import com.epm.user.domain.port.out.DomainEventPublisher;
import com.epm.user.domain.port.out.TeamRepository;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link CreateTeamUseCaseImpl}.
 * RED: CreateTeamUseCaseImpl does not exist yet.
 */
@ExtendWith(MockitoExtension.class)
class CreateTeamUseCaseTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    private CreateTeamUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateTeamUseCaseImpl(teamRepository, eventPublisher);
    }

    @Test
    void happyPathCreatesTeamWithOwnerMembershipAndPublishesEvent() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(teamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateTeamCommand cmd = new CreateTeamCommand("Alpha Team", "A test team");
        TeamResult result = useCase.createTeam(ownerId, tenantId, cmd);

        assertThat(result.name()).isEqualTo("Alpha Team");
        assertThat(result.ownerId()).isEqualTo(ownerId);
        assertThat(result.tenantId()).isEqualTo(tenantId);
        assertThat(result.members()).hasSize(1);
        assertThat(result.members().get(0).role()).isEqualTo(TeamRole.OWNER);
        assertThat(result.members().get(0).userId()).isEqualTo(ownerId);
        verify(eventPublisher).publish(any());
    }

    @Test
    void nameTooLongThrowsValidationException() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String tooLong = "A".repeat(101);
        CreateTeamCommand cmd = new CreateTeamCommand(tooLong, null);

        assertThatThrownBy(() -> useCase.createTeam(ownerId, tenantId, cmd))
                .isInstanceOf(ValidationException.class);
    }
}
