package com.epm.project.application.usecase;

import java.util.List;
import java.util.UUID;

import com.epm.project.domain.port.in.ListProjectsUseCase;
import com.epm.project.domain.port.in.result.ProjectResult;
import com.epm.project.domain.port.out.ProjectRepository;

/**
 * Implementation of {@link ListProjectsUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class ListProjectsUseCaseImpl implements ListProjectsUseCase {

    private final ProjectRepository projectRepository;

    public ListProjectsUseCaseImpl(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Override
    public List<ProjectResult> execute(UUID callerProfileId, UUID tenantId) {
        return execute(callerProfileId, tenantId, false);
    }

    @Override
    public List<ProjectResult> execute(UUID callerProfileId, UUID tenantId, boolean includeArchived) {
        if (includeArchived) {
            return projectRepository.findAllByMemberProfileId(callerProfileId, tenantId)
                    .stream()
                    .map(CreateProjectUseCaseImpl::toResult)
                    .toList();
        }
        return projectRepository.findAllByMemberProfileIdExcludingArchived(callerProfileId, tenantId)
                .stream()
                .map(CreateProjectUseCaseImpl::toResult)
                .toList();
    }
}
