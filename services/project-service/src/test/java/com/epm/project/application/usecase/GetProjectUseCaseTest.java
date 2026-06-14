package com.epm.project.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
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

        ProjectResult result = useCase.execute(project.getId(), ownerId, tenantId, Set.of());

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

        ProjectResult result = useCase.execute(project.getId(), stranger, tenantId, Set.of());

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

        assertThatThrownBy(() -> useCase.execute(project.getId(), stranger, tenantId, Set.of()))
                .isInstanceOf(UnauthorizedProjectAccessException.class);
    }

    @Test
    void team_project_accessible_to_user_in_assigned_team() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha", null, ProjectVisibility.TEAM, ownerId, tenantId));
        project.assignTeam(teamId, ownerId);
        project.pullDomainEvents();
        when(projectRepository.findByIdAndTenantId(project.getId(), tenantId))
                .thenReturn(Optional.of(project));

        // outsider whose team matches an assigned team
        ProjectResult result = useCase.execute(project.getId(), outsider, tenantId, Set.of(teamId));

        assertThat(result.id()).isEqualTo(project.getId());
    }

    @Test
    void team_project_inaccessible_to_non_member_with_empty_team_ids() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha", null, ProjectVisibility.TEAM, ownerId, tenantId));
        project.assignTeam(teamId, ownerId);
        project.pullDomainEvents();
        when(projectRepository.findByIdAndTenantId(project.getId(), tenantId))
                .thenReturn(Optional.of(project));

        // Empty set — TEAM acts as PRIVATE until team claims are wired
        assertThatThrownBy(() -> useCase.execute(project.getId(), stranger, tenantId, Set.of()))
                .isInstanceOf(UnauthorizedProjectAccessException.class);
    }

    @Test
    void missing_project_throws_ProjectNotFoundException() {
        UUID projectId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(projectRepository.findByIdAndTenantId(projectId, tenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(projectId, callerId, tenantId, Set.of()))
                .isInstanceOf(ProjectNotFoundException.class);
    }
}
