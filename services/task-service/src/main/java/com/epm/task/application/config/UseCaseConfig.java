package com.epm.task.application.config;

import com.epm.task.application.usecase.AssignTaskUseCaseImpl;
import com.epm.task.application.usecase.ChangeTaskStatusUseCaseImpl;
import com.epm.task.application.usecase.CreateSubtaskUseCaseImpl;
import com.epm.task.application.usecase.CreateTaskUseCaseImpl;
import com.epm.task.application.usecase.DeleteTaskUseCaseImpl;
import com.epm.task.application.usecase.GetTaskKanbanUseCaseImpl;
import com.epm.task.application.usecase.ListTasksByProjectUseCaseImpl;
import com.epm.task.application.usecase.UpdateTaskUseCaseImpl;
import com.epm.task.domain.port.out.ActivityLogRepository;
import com.epm.task.domain.port.out.DomainEventPublisher;
import com.epm.task.domain.port.out.KanbanViewRepository;
import com.epm.task.domain.port.out.ProjectMembershipPort;
import com.epm.task.domain.port.out.TaskRepository;
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
    CreateTaskUseCaseImpl createTaskUseCase(TaskRepository taskRepository,
            ActivityLogRepository activityLogRepository,
            DomainEventPublisher eventPublisher,
            ProjectMembershipPort membershipPort) {
        return new CreateTaskUseCaseImpl(taskRepository, activityLogRepository,
                eventPublisher, membershipPort);
    }

    @Bean
    CreateSubtaskUseCaseImpl createSubtaskUseCase(TaskRepository taskRepository,
            ActivityLogRepository activityLogRepository,
            DomainEventPublisher eventPublisher) {
        return new CreateSubtaskUseCaseImpl(taskRepository, activityLogRepository, eventPublisher);
    }

    @Bean
    UpdateTaskUseCaseImpl updateTaskUseCase(TaskRepository taskRepository,
            ActivityLogRepository activityLogRepository,
            DomainEventPublisher eventPublisher) {
        return new UpdateTaskUseCaseImpl(taskRepository, activityLogRepository, eventPublisher);
    }

    @Bean
    ChangeTaskStatusUseCaseImpl changeTaskStatusUseCase(TaskRepository taskRepository,
            ActivityLogRepository activityLogRepository,
            DomainEventPublisher eventPublisher) {
        return new ChangeTaskStatusUseCaseImpl(taskRepository, activityLogRepository, eventPublisher);
    }

    @Bean
    AssignTaskUseCaseImpl assignTaskUseCase(TaskRepository taskRepository,
            ActivityLogRepository activityLogRepository,
            DomainEventPublisher eventPublisher) {
        return new AssignTaskUseCaseImpl(taskRepository, activityLogRepository, eventPublisher);
    }

    @Bean
    DeleteTaskUseCaseImpl deleteTaskUseCase(TaskRepository taskRepository,
            ActivityLogRepository activityLogRepository,
            DomainEventPublisher eventPublisher) {
        return new DeleteTaskUseCaseImpl(taskRepository, activityLogRepository, eventPublisher);
    }

    @Bean
    ListTasksByProjectUseCaseImpl listTasksByProjectUseCase(TaskRepository taskRepository) {
        return new ListTasksByProjectUseCaseImpl(taskRepository);
    }

    @Bean
    GetTaskKanbanUseCaseImpl getTaskKanbanUseCase(KanbanViewRepository kanbanViewRepository) {
        return new GetTaskKanbanUseCaseImpl(kanbanViewRepository);
    }
}
