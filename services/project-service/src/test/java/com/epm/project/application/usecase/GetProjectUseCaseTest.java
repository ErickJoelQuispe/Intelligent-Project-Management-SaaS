package com.epm.project.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.epm.project.domain.exception.ProjectNotFoundException;
import com.epm.project.domain.exception.UnauthorizedProjectAccessException;
import com.epm.project.domain.model.Project;
import com.epm.project.domain.model.ProjectVisibility;
import com.epm.project.domain.port.in.command.CreateProjectCommand;
import com.epm.project.domain.port.in.result.ProjectResult;
import com.epm.project.domain.port.out.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link GetProjectUseCaseImpl}.
 * RED phase — implementation does not exist yet.
 */
@ExtendWith(MockitoExtension.class)
class GetProjectUseCaseTest {

    @Mock
    private ProjectRepository projectRepository;

    private GetProjectUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetProjectUseCaseImpl(projectRepository);
    }

    @Test
    void member_can_access_private_project() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha", null, ProjectVisibility.PRIVATE, ownerId, tenantId));
        when(projectRepository.findByIdAndTenantId(project.getId(), tenantId))
                .thenReturn(Optional.of(project));

        ProjectResult result = useCase.execute(project.getId(), ownerId, tenantId);

        assertThat(result.id()).isEqualTo(project.getId());
        assertThat(result.name()).isEqualTo("Alpha");
    }

    @Test
    void public_project_accessible_to_non_member() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha", null, ProjectVisibility.PUBLIC, ownerId, tenantId));
        when(projectRepository.findByIdAndTenantId(project.getId(), tenantId))
                .thenReturn(Optional.of(project));

        ProjectResult result = useCase.execute(project.getId(), stranger, tenantId);

        assertThat(result.id()).isEqualTo(project.getId());
    }

    @Test
    void private_project_inaccessible_to_non_member_throws_UnauthorizedProjectAccessException() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha", null, ProjectVisibility.PRIVATE, ownerId, tenantId));
        when(projectRepository.findByIdAndTenantId(project.getId(), tenantId))
                .thenReturn(Optional.of(project));

        assertThatThrownBy(() -> useCase.execute(project.getId(), stranger, tenantId))
                .isInstanceOf(UnauthorizedProjectAccessException.class);
    }

    @Test
    void missing_project_throws_ProjectNotFoundException() {
        UUID projectId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(projectRepository.findByIdAndTenantId(projectId, tenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(projectId, callerId, tenantId))
                .isInstanceOf(ProjectNotFoundException.class);
    }
}
