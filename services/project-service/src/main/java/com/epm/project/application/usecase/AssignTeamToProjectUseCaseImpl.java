package com.epm.project.application.usecase;

import com.epm.project.domain.exception.ProjectNotFoundException;
import com.epm.project.domain.model.Project;
import com.epm.project.domain.port.in.AssignTeamToProjectUseCase;
import com.epm.project.domain.port.in.command.AssignTeamCommand;
import com.epm.project.domain.port.out.DomainEventPublisher;
import com.epm.project.domain.port.out.ProjectRepository;

/**
 * Implementation of {@link AssignTeamToProjectUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class AssignTeamToProjectUseCaseImpl implements AssignTeamToProjectUseCase {

    private final ProjectRepository projectRepository;
    private final DomainEventPublisher eventPublisher;

    public AssignTeamToProjectUseCaseImpl(ProjectRepository projectRepository,
            DomainEventPublisher eventPublisher) {
        this.projectRepository = projectRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void execute(AssignTeamCommand command) {
        Project project = projectRepository
                .findByIdAndTenantId(command.projectId(), command.tenantId())
                .orElseThrow(() -> new ProjectNotFoundException(command.projectId()));

        project.assignTeam(command.teamId(), command.callerProfileId());

        eventPublisher.publish(project.pullDomainEvents());
        projectRepository.save(project);
    }
}
