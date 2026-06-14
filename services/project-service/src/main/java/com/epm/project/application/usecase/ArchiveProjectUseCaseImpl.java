package com.epm.project.application.usecase;

import java.util.UUID;

import com.epm.project.domain.exception.ProjectNotFoundException;
import com.epm.project.domain.model.Project;
import com.epm.project.domain.port.in.ArchiveProjectUseCase;
import com.epm.project.domain.port.out.ProjectRepository;
import com.epm.project.domain.port.out.TransactionalOutboxWriter;

/**
 * Implementation of {@link ArchiveProjectUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 *
 * <p>Uses {@link TransactionalOutboxWriter} so aggregate save and outbox event
 * insertion happen atomically in one transaction (FIX 1). If the project is already
 * archived, {@link Project#archive(UUID)} is a no-op that emits no duplicate event
 * (FIX 5), so this use case is safe to re-invoke with the same project.
 */
public class ArchiveProjectUseCaseImpl implements ArchiveProjectUseCase {

    private final ProjectRepository projectRepository;
    private final TransactionalOutboxWriter outboxWriter;

    public ArchiveProjectUseCaseImpl(ProjectRepository projectRepository,
            TransactionalOutboxWriter outboxWriter) {
        this.projectRepository = projectRepository;
        this.outboxWriter = outboxWriter;
    }

    @Override
    public void execute(UUID projectId, UUID callerProfileId, UUID tenantId) {
        Project project = projectRepository.findByIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        project.archive(callerProfileId);

        outboxWriter.saveAndPublish(project);
    }
}
