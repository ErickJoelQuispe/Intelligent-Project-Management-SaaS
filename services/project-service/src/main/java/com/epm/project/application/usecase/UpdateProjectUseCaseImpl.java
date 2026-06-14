package com.epm.project.application.usecase;

import com.epm.project.domain.exception.ProjectNotFoundException;
import com.epm.project.domain.model.Project;
import com.epm.project.domain.port.in.UpdateProjectUseCase;
import com.epm.project.domain.port.in.command.UpdateProjectCommand;
import com.epm.project.domain.port.in.result.ProjectResult;
import com.epm.project.domain.port.out.ProjectRepository;
import com.epm.project.domain.port.out.TransactionalOutboxWriter;

/**
 * Implementation of {@link UpdateProjectUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 *
 * <p>Uses {@link TransactionalOutboxWriter} so aggregate save and outbox event
 * insertion happen atomically in one transaction (FIX 1).
 */
public class UpdateProjectUseCaseImpl implements UpdateProjectUseCase {

    private final ProjectRepository projectRepository;
    private final TransactionalOutboxWriter outboxWriter;

    public UpdateProjectUseCaseImpl(ProjectRepository projectRepository,
            TransactionalOutboxWriter outboxWriter) {
        this.projectRepository = projectRepository;
        this.outboxWriter = outboxWriter;
    }

    @Override
    public ProjectResult execute(UpdateProjectCommand command) {
        Project project = projectRepository
                .findByIdAndTenantId(command.projectId(), command.tenantId())
                .orElseThrow(() -> new ProjectNotFoundException(command.projectId()));

        project.update(command.name(), command.description(),
                command.visibility(), command.callerProfileId());

        Project saved = outboxWriter.saveAndPublish(project);

        return CreateProjectUseCaseImpl.toResult(saved);
    }
}
