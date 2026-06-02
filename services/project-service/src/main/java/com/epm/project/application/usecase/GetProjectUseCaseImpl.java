package com.epm.project.application.usecase;

import java.util.UUID;

import com.epm.project.domain.exception.ProjectNotFoundException;
import com.epm.project.domain.exception.UnauthorizedProjectAccessException;
import com.epm.project.domain.model.Project;
import com.epm.project.domain.model.ProjectVisibility;
import com.epm.project.domain.port.in.GetProjectUseCase;
import com.epm.project.domain.port.in.result.ProjectResult;
import com.epm.project.domain.port.out.ProjectRepository;

/**
 * Implementation of {@link GetProjectUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class GetProjectUseCaseImpl implements GetProjectUseCase {

    private final ProjectRepository projectRepository;

    public GetProjectUseCaseImpl(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Override
    public ProjectResult execute(UUID projectId, UUID callerProfileId, UUID tenantId) {
        Project project = projectRepository.findByIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        boolean canAccess = project.isMember(callerProfileId)
                || project.getVisibility() == ProjectVisibility.PUBLIC;

        if (!canAccess) {
            throw new UnauthorizedProjectAccessException(callerProfileId, projectId);
        }

        return CreateProjectUseCaseImpl.toResult(project);
    }
}
