package com.epm.project.application.usecase;

import java.util.Set;
import java.util.UUID;

import com.epm.project.domain.exception.ProjectNotFoundException;
import com.epm.project.domain.exception.UnauthorizedProjectAccessException;
import com.epm.project.domain.model.Project;
import com.epm.project.domain.port.in.GetProjectUseCase;
import com.epm.project.domain.port.in.result.ProjectResult;
import com.epm.project.domain.port.out.ProjectRepository;

/**
 * Implementation of {@link GetProjectUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 *
 * <p>Access control uses {@link Project#canBeAccessedBy(UUID, Set)} to apply
 * the correct TEAM visibility semantics (FIX 7). The {@code callerTeamIds} set
 * is passed in by the controller; until team claims are wired in the JWT or via
 * a user-service lookup, the controller passes an empty set, which means TEAM
 * projects behave as PRIVATE at runtime.
 */
public class GetProjectUseCaseImpl implements GetProjectUseCase {

    private final ProjectRepository projectRepository;

    public GetProjectUseCaseImpl(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Override
    public ProjectResult execute(UUID projectId, UUID callerProfileId, UUID tenantId,
            Set<UUID> callerTeamIds) {
        Project project = projectRepository.findByIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        if (!project.canBeAccessedBy(callerProfileId, callerTeamIds)) {
            throw new UnauthorizedProjectAccessException(callerProfileId, projectId);
        }

        return CreateProjectUseCaseImpl.toResult(project);
    }
}
