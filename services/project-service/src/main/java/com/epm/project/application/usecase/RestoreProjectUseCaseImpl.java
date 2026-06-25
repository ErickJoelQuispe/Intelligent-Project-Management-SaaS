package com.epm.project.application.usecase;

import java.util.UUID;

import com.epm.project.domain.exception.ProjectNotFoundException;
import com.epm.project.domain.model.Project;
import com.epm.project.domain.port.in.RestoreProjectUseCase;
import com.epm.project.domain.port.in.result.ProjectResult;
import com.epm.project.domain.port.out.ProjectRepository;
import com.epm.project.domain.port.out.TransactionalOutboxWriter;

/**
 * Implementation of {@link RestoreProjectUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 *
 * <p>Uses {@link TransactionalOutboxWriter} so aggregate save and outbox event
 * insertion happen atomically in one transaction. If the project is already
 * active, {@link Project#restore(UUID)} is a no-op that emits no duplicate event,
 * so this use case is safe to re-invoke with the same project.
 */
public class RestoreProjectUseCaseImpl implements RestoreProjectUseCase {

    private final ProjectRepository projectRepository;
    private final TransactionalOutboxWriter outboxWriter;

    public RestoreProjectUseCaseImpl(ProjectRepository projectRepository,
            TransactionalOutboxWriter outboxWriter) {
        this.projectRepository = projectRepository;
        this.outboxWriter = outboxWriter;
    }

    @Override
    public ProjectResult execute(UUID projectId, UUID callerProfileId, UUID tenantId) {
        Project project = projectRepository.findByIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        project.restore(callerProfileId);

        Project saved = outboxWriter.saveAndPublish(project);

        return CreateProjectUseCaseImpl.toResult(saved);
    }
}
