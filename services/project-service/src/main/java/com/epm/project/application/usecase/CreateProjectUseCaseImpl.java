package com.epm.project.application.usecase;

import java.util.List;

import com.epm.project.domain.model.Project;
import com.epm.project.domain.port.in.CreateProjectUseCase;
import com.epm.project.domain.port.in.command.CreateProjectCommand;
import com.epm.project.domain.port.in.result.ProjectMemberResult;
import com.epm.project.domain.port.in.result.ProjectResult;
import com.epm.project.domain.port.in.result.ProjectTeamResult;
import com.epm.project.domain.port.out.TransactionalOutboxWriter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Implementation of {@link CreateProjectUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 *
 * <p>Uses {@link TransactionalOutboxWriter} so aggregate save and outbox event
 * insertion happen atomically in one transaction (FIX 1).
 * The duplicate name validation guard was removed (FIX 11): {@link Project#create}
 * already throws {@link IllegalArgumentException} for blank or oversized names,
 * which {@code GlobalExceptionHandler} maps to 400.
 */
public class CreateProjectUseCaseImpl implements CreateProjectUseCase {

    private final TransactionalOutboxWriter outboxWriter;
    private final MeterRegistry meterRegistry;

    public CreateProjectUseCaseImpl(TransactionalOutboxWriter outboxWriter,
            MeterRegistry meterRegistry) {
        this.outboxWriter = outboxWriter;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public ProjectResult execute(CreateProjectCommand command) {
        Project project = Project.create(command);
        Project saved = outboxWriter.saveAndPublish(project);

        Counter.builder("projects.created")
                .tag("tenantId", command.tenantId().toString())
                .register(meterRegistry)
                .increment();

        return toResult(saved);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    static ProjectResult toResult(Project project) {
        List<ProjectTeamResult> teamResults = project.getTeams().stream()
                .filter(t -> t.isActive())
                .map(t -> new ProjectTeamResult(t.getId(), t.getProjectId(), t.getTeamId(), t.getAssignedAt()))
                .toList();

        List<ProjectMemberResult> memberResults = project.getMembers().stream()
                .filter(m -> m.isActive())
                .map(m -> new ProjectMemberResult(
                        m.getId(), m.getProjectId(), m.getProfileId(),
                        m.getRole().name(), m.getJoinedAt()))
                .toList();

        return new ProjectResult(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus().name(),
                project.getVisibility().name(),
                project.getOwnerProfileId(),
                project.getTenantId(),
                teamResults,
                memberResults,
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
