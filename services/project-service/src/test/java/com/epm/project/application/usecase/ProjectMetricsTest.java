package com.epm.project.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.epm.project.domain.model.ProjectVisibility;
import com.epm.project.domain.port.in.command.CreateProjectCommand;
import com.epm.project.domain.port.out.DomainEventPublisher;
import com.epm.project.domain.port.out.ProjectRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests verifying that {@link CreateProjectUseCaseImpl} increments the
 * {@code projects.created} Micrometer counter on each successful project creation.
 *
 * <p>Uses {@link SimpleMeterRegistry} — no Prometheus or Spring context needed.
 */
@ExtendWith(MockitoExtension.class)
class ProjectMetricsTest {

    @Mock
    ProjectRepository projectRepository;

    @Mock
    DomainEventPublisher eventPublisher;

    SimpleMeterRegistry meterRegistry;
    CreateProjectUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        useCase = new CreateProjectUseCaseImpl(projectRepository, eventPublisher, meterRegistry);
    }

    @Test
    void createProject_incrementsProjectsCreatedCounter() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateProjectCommand command = new CreateProjectCommand(
                "My Project", "Description", ProjectVisibility.PRIVATE, ownerId, tenantId);

        useCase.execute(command);

        Counter counter = meterRegistry.find("projects.created")
                .tag("tenantId", tenantId.toString())
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void createProject_differentTenants_counterTaggedByTenantId() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new CreateProjectCommand("Project A", null, ProjectVisibility.PRIVATE, ownerId, tenantA));
        useCase.execute(new CreateProjectCommand("Project B", null, ProjectVisibility.PUBLIC, ownerId, tenantB));

        Counter counterA = meterRegistry.find("projects.created")
                .tag("tenantId", tenantA.toString())
                .counter();
        Counter counterB = meterRegistry.find("projects.created")
                .tag("tenantId", tenantB.toString())
                .counter();

        assertThat(counterA).isNotNull();
        assertThat(counterA.count()).isEqualTo(1.0);
        assertThat(counterB).isNotNull();
        assertThat(counterB.count()).isEqualTo(1.0);
    }
}
