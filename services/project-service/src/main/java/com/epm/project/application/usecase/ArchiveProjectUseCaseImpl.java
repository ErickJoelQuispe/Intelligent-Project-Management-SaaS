package com.epm.project.application.usecase;

import java.util.UUID;

import com.epm.project.domain.exception.ProjectNotFoundException;
import com.epm.project.domain.model.Project;
import com.epm.project.domain.port.in.ArchiveProjectUseCase;
import com.epm.project.domain.port.out.DomainEventPublisher;
import com.epm.project.domain.port.out.ProjectRepository;

/**
 * Implementation of {@link ArchiveProjectUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class ArchiveProjectUseCaseImpl implements ArchiveProjectUseCase {

    private final ProjectRepository projectRepository;
    private final DomainEventPublisher eventPublisher;

    public ArchiveProjectUseCaseImpl(ProjectRepository projectRepository,
            DomainEventPublisher eventPublisher) {
        this.projectRepository = projectRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void execute(UUID projectId, UUID callerProfileId, UUID tenantId) {
        Project project = projectRepository.findByIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        project.archive(callerProfileId);

        eventPublisher.publish(project.pullDomainEvents());
        projectRepository.save(project);
    }
}
