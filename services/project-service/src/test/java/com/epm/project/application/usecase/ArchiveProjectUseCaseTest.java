package com.epm.project.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.epm.project.domain.exception.ProjectNotFoundException;
import com.epm.project.domain.exception.UnauthorizedProjectAccessException;
import com.epm.project.domain.model.Project;
import com.epm.project.domain.model.ProjectRole;
import com.epm.project.domain.model.ProjectVisibility;
import com.epm.project.domain.port.in.command.CreateProjectCommand;
import com.epm.project.domain.port.out.ProjectRepository;
import com.epm.project.domain.port.out.TransactionalOutboxWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ArchiveProjectUseCaseImpl}.
 */
@ExtendWith(MockitoExtension.class)
class ArchiveProjectUseCaseTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TransactionalOutboxWriter outboxWriter;

    private ArchiveProjectUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new ArchiveProjectUseCaseImpl(projectRepository, outboxWriter);
    }

    @Test
    void owner_can_archive_project() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha", null, ProjectVisibility.PRIVATE, ownerId, tenantId));
        when(projectRepository.findByIdAndTenantId(project.getId(), tenantId))
                .thenReturn(Optional.of(project));
        when(outboxWriter.saveAndPublish(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(project.getId(), ownerId, tenantId);

        verify(outboxWriter).saveAndPublish(any());
    }

    @Test
    void manager_cannot_archive_throws_UnauthorizedProjectAccessException() {
        UUID ownerId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha", null, ProjectVisibility.PRIVATE, ownerId, tenantId));
        project.addMember(managerId, ProjectRole.MANAGER, ownerId);
        when(projectRepository.findByIdAndTenantId(project.getId(), tenantId))
                .thenReturn(Optional.of(project));

        assertThatThrownBy(() -> useCase.execute(project.getId(), managerId, tenantId))
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
