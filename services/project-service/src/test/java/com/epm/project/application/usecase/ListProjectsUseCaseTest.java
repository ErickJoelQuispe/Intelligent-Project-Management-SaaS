package com.epm.project.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

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
 * Unit tests for {@link ListProjectsUseCaseImpl}.
 * RED phase — implementation does not exist yet.
 */
@ExtendWith(MockitoExtension.class)
class ListProjectsUseCaseTest {

    @Mock
    private ProjectRepository projectRepository;

    private ListProjectsUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListProjectsUseCaseImpl(projectRepository);
    }

    @Test
    void returns_list_of_projects_for_caller_excluding_archived_by_default() {
        UUID callerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Project p1 = Project.create(new CreateProjectCommand(
                "Alpha", null, ProjectVisibility.PRIVATE, callerId, tenantId));
        Project p2 = Project.create(new CreateProjectCommand(
                "Beta", null, ProjectVisibility.TEAM, callerId, tenantId));
        when(projectRepository.findAllByMemberProfileIdExcludingArchived(callerId, tenantId))
                .thenReturn(List.of(p1, p2));

        List<ProjectResult> results = useCase.execute(callerId, tenantId);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(ProjectResult::name)
                .containsExactlyInAnyOrder("Alpha", "Beta");
    }

    @Test
    void returns_empty_list_when_no_projects_found() {
        UUID callerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(projectRepository.findAllByMemberProfileIdExcludingArchived(any(), any()))
                .thenReturn(Collections.emptyList());

        List<ProjectResult> results = useCase.execute(callerId, tenantId);

        assertThat(results).isEmpty();
    }

    @Test
    void returns_archived_projects_when_includeArchived_true() {
        UUID callerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Project p1 = Project.create(new CreateProjectCommand(
                "Alpha", null, ProjectVisibility.PRIVATE, callerId, tenantId));
        // Simulate archived project by reconstituting with ARCHIVED status
        Project archived = Project.create(new CreateProjectCommand(
                "Archived Project", null, ProjectVisibility.PRIVATE, callerId, tenantId));
        when(projectRepository.findAllByMemberProfileId(callerId, tenantId))
                .thenReturn(List.of(p1, archived));

        List<ProjectResult> results = useCase.execute(callerId, tenantId, true);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(ProjectResult::name)
                .containsExactlyInAnyOrder("Alpha", "Archived Project");
    }
}
