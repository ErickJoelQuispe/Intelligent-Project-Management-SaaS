package com.epm.project.application.usecase;

import java.util.List;

import com.epm.project.domain.model.Project;
import com.epm.project.domain.port.in.CreateProjectUseCase;
import com.epm.project.domain.port.in.command.CreateProjectCommand;
import com.epm.project.domain.port.in.result.ProjectMemberResult;
import com.epm.project.domain.port.in.result.ProjectResult;
import com.epm.project.domain.port.in.result.ProjectTeamResult;
import com.epm.project.domain.port.out.DomainEventPublisher;
import com.epm.project.domain.port.out.ProjectRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.ValidationException;

/**
 * Implementation of {@link CreateProjectUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class CreateProjectUseCaseImpl implements CreateProjectUseCase {

    private final ProjectRepository projectRepository;
    private final DomainEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    public CreateProjectUseCaseImpl(ProjectRepository projectRepository,
            DomainEventPublisher eventPublisher,
            MeterRegistry meterRegistry) {
        this.projectRepository = projectRepository;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public ProjectResult execute(CreateProjectCommand command) {
        if (command.name() == null || command.name().isBlank()) {
            throw new ValidationException("Project name must not be blank");
        }
        if (command.name().length() > 100) {
            throw new ValidationException("Project name must not exceed 100 characters");
        }

        Project project = Project.create(command);
        eventPublisher.publish(project.pullDomainEvents());
        Project saved = projectRepository.save(project);

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
