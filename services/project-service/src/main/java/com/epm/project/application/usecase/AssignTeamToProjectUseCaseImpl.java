package com.epm.project.application.usecase;

import com.epm.project.domain.exception.ProjectNotFoundException;
import com.epm.project.domain.model.Project;
import com.epm.project.domain.port.in.AssignTeamToProjectUseCase;
import com.epm.project.domain.port.in.command.AssignTeamCommand;
import com.epm.project.domain.port.out.ProjectRepository;
import com.epm.project.domain.port.out.TransactionalOutboxWriter;

/**
 * Implementation of {@link AssignTeamToProjectUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 *
 * <p>Uses {@link TransactionalOutboxWriter} so aggregate save and outbox event
 * insertion happen atomically in one transaction (FIX 1).
 */
public class AssignTeamToProjectUseCaseImpl implements AssignTeamToProjectUseCase {

    private final ProjectRepository projectRepository;
    private final TransactionalOutboxWriter outboxWriter;

    public AssignTeamToProjectUseCaseImpl(ProjectRepository projectRepository,
            TransactionalOutboxWriter outboxWriter) {
        this.projectRepository = projectRepository;
        this.outboxWriter = outboxWriter;
    }

    @Override
    public void execute(AssignTeamCommand command) {
        Project project = projectRepository
                .findByIdAndTenantId(command.projectId(), command.tenantId())
                .orElseThrow(() -> new ProjectNotFoundException(command.projectId()));

        project.assignTeam(command.teamId(), command.callerProfileId());

        outboxWriter.saveAndPublish(project);
    }
}
