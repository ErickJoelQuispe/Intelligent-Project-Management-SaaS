package com.epm.project.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.epm.project.domain.model.ProjectRole;
import com.epm.project.domain.model.ProjectVisibility;
import com.epm.project.domain.port.in.command.CreateProjectCommand;
import com.epm.project.domain.port.in.result.ProjectResult;
import com.epm.project.domain.port.out.DomainEventPublisher;
import com.epm.project.domain.port.out.ProjectRepository;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link CreateProjectUseCaseImpl}.
 * RED phase — implementation does not exist yet.
 */
@ExtendWith(MockitoExtension.class)
class CreateProjectUseCaseTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    private CreateProjectUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateProjectUseCaseImpl(projectRepository, eventPublisher);
    }

    @Test
    void happyPath_creates_project_with_owner_membership_and_publishes_events() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateProjectCommand cmd = new CreateProjectCommand(
                "Alpha Project", "A description", ProjectVisibility.PRIVATE, ownerId, tenantId);
        ProjectResult result = useCase.execute(cmd);

        assertThat(result.name()).isEqualTo("Alpha Project");
        assertThat(result.ownerProfileId()).isEqualTo(ownerId);
        assertThat(result.tenantId()).isEqualTo(tenantId);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.members()).hasSize(1);
        assertThat(result.members().get(0).role()).isEqualTo(ProjectRole.OWNER.name());
        verify(eventPublisher).publish(any());
    }

    @Test
    void blank_name_throws_ValidationException() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CreateProjectCommand cmd = new CreateProjectCommand(
                "  ", null, ProjectVisibility.PRIVATE, ownerId, tenantId);

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("name");
    }

    @Test
    void name_over_100_chars_throws_ValidationException() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CreateProjectCommand cmd = new CreateProjectCommand(
                "A".repeat(101), null, ProjectVisibility.PRIVATE, ownerId, tenantId);

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("name");
    }
}
