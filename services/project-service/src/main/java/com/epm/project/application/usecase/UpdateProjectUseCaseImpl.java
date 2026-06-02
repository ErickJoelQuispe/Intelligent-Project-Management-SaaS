package com.epm.project.application.usecase;

import com.epm.project.domain.exception.ProjectNotFoundException;
import com.epm.project.domain.model.Project;
import com.epm.project.domain.port.in.UpdateProjectUseCase;
import com.epm.project.domain.port.in.command.UpdateProjectCommand;
import com.epm.project.domain.port.in.result.ProjectResult;
import com.epm.project.domain.port.out.DomainEventPublisher;
import com.epm.project.domain.port.out.ProjectRepository;

/**
 * Implementation of {@link UpdateProjectUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class UpdateProjectUseCaseImpl implements UpdateProjectUseCase {

    private final ProjectRepository projectRepository;
    private final DomainEventPublisher eventPublisher;

    public UpdateProjectUseCaseImpl(ProjectRepository projectRepository,
            DomainEventPublisher eventPublisher) {
        this.projectRepository = projectRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public ProjectResult execute(UpdateProjectCommand command) {
        Project project = projectRepository
                .findByIdAndTenantId(command.projectId(), command.tenantId())
                .orElseThrow(() -> new ProjectNotFoundException(command.projectId()));

        project.update(command.name(), command.description(),
                command.visibility(), command.callerProfileId());

        eventPublisher.publish(project.pullDomainEvents());
        Project saved = projectRepository.save(project);

        return CreateProjectUseCaseImpl.toResult(saved);
    }
}
