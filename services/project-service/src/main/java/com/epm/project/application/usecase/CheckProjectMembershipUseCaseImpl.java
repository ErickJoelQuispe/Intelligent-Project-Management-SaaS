package com.epm.project.application.usecase;

import java.util.UUID;

import com.epm.project.domain.port.in.CheckProjectMembershipUseCase;
import com.epm.project.domain.port.out.ProjectRepository;

/**
 * Implementation of {@link CheckProjectMembershipUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class CheckProjectMembershipUseCaseImpl implements CheckProjectMembershipUseCase {

    private final ProjectRepository projectRepository;

    public CheckProjectMembershipUseCaseImpl(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Override
    public boolean isMember(UUID projectId, UUID userId, UUID tenantId) {
        return projectRepository
                .findByIdAndTenantId(projectId, tenantId)
                .map(project -> project.isMember(userId))
                .orElse(false);
    }
}
