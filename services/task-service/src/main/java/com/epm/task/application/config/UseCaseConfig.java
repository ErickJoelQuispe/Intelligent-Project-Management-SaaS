package com.epm.task.application.config;

import com.epm.task.application.usecase.AssignTaskUseCaseImpl;
import com.epm.task.application.usecase.ChangeTaskStatusUseCaseImpl;
import com.epm.task.application.usecase.CreateSubtaskUseCaseImpl;
import com.epm.task.application.usecase.CreateTaskUseCaseImpl;
import com.epm.task.application.usecase.DeleteTaskUseCaseImpl;
import com.epm.task.application.usecase.GetSubtasksUseCaseImpl;
import com.epm.task.application.usecase.GetTaskKanbanUseCaseImpl;
import com.epm.task.application.usecase.ListTasksByProjectUseCaseImpl;
import com.epm.task.application.usecase.UpdateTaskUseCaseImpl;
import com.epm.task.domain.port.out.KanbanViewRepository;
import com.epm.task.domain.port.out.ProjectMembershipPort;
import com.epm.task.domain.port.out.TaskRepository;
import com.epm.task.domain.port.out.TransactionalOutboxWriter;
import io.micrometer.core.instrument.MeterRegistry;
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
    CreateTaskUseCaseImpl createTaskUseCase(TransactionalOutboxWriter outboxWriter,
            ProjectMembershipPort membershipPort,
            MeterRegistry meterRegistry) {
        return new CreateTaskUseCaseImpl(outboxWriter, membershipPort, meterRegistry);
    }

    @Bean
    CreateSubtaskUseCaseImpl createSubtaskUseCase(TaskRepository taskRepository,
            TransactionalOutboxWriter outboxWriter,
            ProjectMembershipPort membershipPort) {
        return new CreateSubtaskUseCaseImpl(taskRepository, outboxWriter, membershipPort);
    }

    @Bean
    UpdateTaskUseCaseImpl updateTaskUseCase(TaskRepository taskRepository,
            TransactionalOutboxWriter outboxWriter,
            ProjectMembershipPort membershipPort) {
        return new UpdateTaskUseCaseImpl(taskRepository, outboxWriter, membershipPort);
    }

    @Bean
    ChangeTaskStatusUseCaseImpl changeTaskStatusUseCase(TaskRepository taskRepository,
            TransactionalOutboxWriter outboxWriter,
            ProjectMembershipPort membershipPort) {
        return new ChangeTaskStatusUseCaseImpl(taskRepository, outboxWriter, membershipPort);
    }

    @Bean
    AssignTaskUseCaseImpl assignTaskUseCase(TaskRepository taskRepository,
            TransactionalOutboxWriter outboxWriter,
            ProjectMembershipPort membershipPort) {
        return new AssignTaskUseCaseImpl(taskRepository, outboxWriter, membershipPort);
    }

    @Bean
    DeleteTaskUseCaseImpl deleteTaskUseCase(TaskRepository taskRepository,
            TransactionalOutboxWriter outboxWriter,
            ProjectMembershipPort membershipPort) {
        return new DeleteTaskUseCaseImpl(taskRepository, outboxWriter, membershipPort);
    }

    @Bean
    GetSubtasksUseCaseImpl getSubtasksUseCase(TaskRepository taskRepository) {
        return new GetSubtasksUseCaseImpl(taskRepository);
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
