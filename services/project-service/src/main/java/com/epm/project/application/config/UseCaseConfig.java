package com.epm.project.application.config;

import com.epm.project.application.usecase.ArchiveProjectUseCaseImpl;
import com.epm.project.application.usecase.AssignTeamToProjectUseCaseImpl;
import com.epm.project.application.usecase.CreateProjectUseCaseImpl;
import com.epm.project.application.usecase.GetProjectUseCaseImpl;
import com.epm.project.application.usecase.ListProjectsUseCaseImpl;
import com.epm.project.application.usecase.UpdateProjectUseCaseImpl;
import com.epm.project.domain.port.out.DomainEventPublisher;
import com.epm.project.domain.port.out.ProjectRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that wires use case implementations to their port interfaces.
 *
 * <p>Use case implementations are pure Java (no Spring annotations).
 * This configuration class is the only place they are coupled to Spring.
 */
@Configuration
public class UseCaseConfig {

    @Bean
    CreateProjectUseCaseImpl createProjectUseCase(ProjectRepository projectRepository,
            DomainEventPublisher eventPublisher) {
        return new CreateProjectUseCaseImpl(projectRepository, eventPublisher);
    }

    @Bean
    ListProjectsUseCaseImpl listProjectsUseCase(ProjectRepository projectRepository) {
        return new ListProjectsUseCaseImpl(projectRepository);
    }

    @Bean
    GetProjectUseCaseImpl getProjectUseCase(ProjectRepository projectRepository) {
        return new GetProjectUseCaseImpl(projectRepository);
    }

    @Bean
    UpdateProjectUseCaseImpl updateProjectUseCase(ProjectRepository projectRepository,
            DomainEventPublisher eventPublisher) {
        return new UpdateProjectUseCaseImpl(projectRepository, eventPublisher);
    }

    @Bean
    ArchiveProjectUseCaseImpl archiveProjectUseCase(ProjectRepository projectRepository,
            DomainEventPublisher eventPublisher) {
        return new ArchiveProjectUseCaseImpl(projectRepository, eventPublisher);
    }

    @Bean
    AssignTeamToProjectUseCaseImpl assignTeamToProjectUseCase(ProjectRepository projectRepository,
            DomainEventPublisher eventPublisher) {
        return new AssignTeamToProjectUseCaseImpl(projectRepository, eventPublisher);
    }
}
