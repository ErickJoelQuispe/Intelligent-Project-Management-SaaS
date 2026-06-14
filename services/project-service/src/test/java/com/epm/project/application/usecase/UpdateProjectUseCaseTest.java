package com.epm.project.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.epm.project.domain.exception.ProjectNotFoundException;
import com.epm.project.domain.exception.UnauthorizedProjectAccessException;
import com.epm.project.domain.model.Project;
import com.epm.project.domain.model.ProjectRole;
import com.epm.project.domain.model.ProjectVisibility;
import com.epm.project.domain.port.in.command.CreateProjectCommand;
import com.epm.project.domain.port.in.command.UpdateProjectCommand;
import com.epm.project.domain.port.in.result.ProjectResult;
import com.epm.project.domain.port.out.ProjectRepository;
import com.epm.project.domain.port.out.TransactionalOutboxWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link UpdateProjectUseCaseImpl}.
 */
@ExtendWith(MockitoExtension.class)
class UpdateProjectUseCaseTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TransactionalOutboxWriter outboxWriter;

    private UpdateProjectUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateProjectUseCaseImpl(projectRepository, outboxWriter);
    }

    @Test
    void owner_can_update_project() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Old Name", "Old desc", ProjectVisibility.PRIVATE, ownerId, tenantId));
        when(projectRepository.findByIdAndTenantId(project.getId(), tenantId))
                .thenReturn(Optional.of(project));
        when(outboxWriter.saveAndPublish(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProjectCommand cmd = new UpdateProjectCommand(
                project.getId(), "New Name", "New desc", ProjectVisibility.PUBLIC, ownerId, tenantId);
        ProjectResult result = useCase.execute(cmd);

        assertThat(result.name()).isEqualTo("New Name");
        assertThat(result.description()).isEqualTo("New desc");
        assertThat(result.visibility()).isEqualTo("PUBLIC");
    }

    @Test
    void contributor_cannot_update_throws_UnauthorizedProjectAccessException() {
        UUID ownerId = UUID.randomUUID();
        UUID contributorId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha", null, ProjectVisibility.PRIVATE, ownerId, tenantId));
        project.addMember(contributorId, ProjectRole.CONTRIBUTOR, ownerId);
        when(projectRepository.findByIdAndTenantId(project.getId(), tenantId))
                .thenReturn(Optional.of(project));

        UpdateProjectCommand cmd = new UpdateProjectCommand(
                project.getId(), "Hacked", null, ProjectVisibility.PUBLIC, contributorId, tenantId);

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(UnauthorizedProjectAccessException.class);
    }

    @Test
    void missing_project_throws_ProjectNotFoundException() {
        UUID projectId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(projectRepository.findByIdAndTenantId(projectId, tenantId))
                .thenReturn(Optional.empty());

        UpdateProjectCommand cmd = new UpdateProjectCommand(
                projectId, "X", null, ProjectVisibility.PRIVATE, callerId, tenantId);

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(ProjectNotFoundException.class);
    }
}
