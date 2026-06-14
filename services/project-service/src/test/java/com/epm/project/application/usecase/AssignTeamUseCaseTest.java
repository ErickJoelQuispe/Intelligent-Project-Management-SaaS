package com.epm.project.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.epm.project.domain.exception.DuplicateTeamAssignmentException;
import com.epm.project.domain.exception.ProjectNotFoundException;
import com.epm.project.domain.exception.UnauthorizedProjectAccessException;
import com.epm.project.domain.model.Project;
import com.epm.project.domain.model.ProjectRole;
import com.epm.project.domain.model.ProjectVisibility;
import com.epm.project.domain.port.in.command.AssignTeamCommand;
import com.epm.project.domain.port.in.command.CreateProjectCommand;
import com.epm.project.domain.port.out.ProjectRepository;
import com.epm.project.domain.port.out.TransactionalOutboxWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AssignTeamToProjectUseCaseImpl}.
 */
@ExtendWith(MockitoExtension.class)
class AssignTeamUseCaseTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TransactionalOutboxWriter outboxWriter;

    private AssignTeamToProjectUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new AssignTeamToProjectUseCaseImpl(projectRepository, outboxWriter);
    }

    @Test
    void owner_can_assign_team_successfully() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha", null, ProjectVisibility.PRIVATE, ownerId, tenantId));
        when(projectRepository.findByIdAndTenantId(project.getId(), tenantId))
                .thenReturn(Optional.of(project));
        when(outboxWriter.saveAndPublish(any())).thenAnswer(inv -> inv.getArgument(0));

        AssignTeamCommand cmd = new AssignTeamCommand(project.getId(), teamId, ownerId, tenantId);
        useCase.execute(cmd);

        verify(outboxWriter).saveAndPublish(any());
    }

    @Test
    void duplicate_team_assignment_throws_DuplicateTeamAssignmentException() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha", null, ProjectVisibility.PRIVATE, ownerId, tenantId));
        project.assignTeam(teamId, ownerId); // first assignment
        project.pullDomainEvents();
        when(projectRepository.findByIdAndTenantId(project.getId(), tenantId))
                .thenReturn(Optional.of(project));

        AssignTeamCommand cmd = new AssignTeamCommand(project.getId(), teamId, ownerId, tenantId);

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(DuplicateTeamAssignmentException.class);
    }

    @Test
    void contributor_cannot_assign_team_throws_UnauthorizedProjectAccessException() {
        UUID ownerId = UUID.randomUUID();
        UUID contributorId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha", null, ProjectVisibility.PRIVATE, ownerId, tenantId));
        project.addMember(contributorId, ProjectRole.CONTRIBUTOR, ownerId);
        when(projectRepository.findByIdAndTenantId(project.getId(), tenantId))
                .thenReturn(Optional.of(project));

        AssignTeamCommand cmd = new AssignTeamCommand(
                project.getId(), teamId, contributorId, tenantId);

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(UnauthorizedProjectAccessException.class);
    }

    @Test
    void missing_project_throws_ProjectNotFoundException() {
        UUID projectId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        when(projectRepository.findByIdAndTenantId(projectId, tenantId))
                .thenReturn(Optional.empty());

        AssignTeamCommand cmd = new AssignTeamCommand(projectId, teamId, callerId, tenantId);

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(ProjectNotFoundException.class);
    }
}
