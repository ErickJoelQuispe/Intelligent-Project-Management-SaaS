package com.epm.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.epm.task.application.usecase.UpdateTaskUseCaseImpl;
import com.epm.task.domain.exception.TaskNotFoundException;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.model.TaskStatus;
import com.epm.task.domain.port.in.command.CreateTaskCommand;
import com.epm.task.domain.port.in.command.UpdateTaskCommand;
import com.epm.task.domain.port.in.result.TaskResult;
import com.epm.task.domain.port.out.ActivityLogRepository;
import com.epm.task.domain.port.out.DomainEventPublisher;
import com.epm.task.domain.port.out.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for UpdateTaskUseCaseImpl.
 */
@ExtendWith(MockitoExtension.class)
class UpdateTaskUseCaseImplTest {

    @Mock TaskRepository taskRepository;
    @Mock ActivityLogRepository activityLogRepository;
    @Mock DomainEventPublisher eventPublisher;

    UpdateTaskUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateTaskUseCaseImpl(taskRepository, activityLogRepository, eventPublisher);
    }

    @Test
    void execute_existingTask_updatesFieldsAndPublishesEvent() {
        UUID tenantId = UUID.randomUUID();
        Task task = Task.create(new CreateTaskCommand(
                tenantId, UUID.randomUUID(), UUID.randomUUID(),
                "Old title", null, TaskPriority.LOW, null, null));
        task.pullDomainEvents();

        when(taskRepository.findByIdAndTenantId(task.getId(), tenantId))
                .thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTaskCommand command = new UpdateTaskCommand(
                task.getId(), tenantId, UUID.randomUUID(),
                "New title", "Description updated", TaskPriority.HIGH, null);

        TaskResult result = useCase.execute(command);

        assertThat(result.title()).isEqualTo("New title");
        assertThat(result.description()).isEqualTo("Description updated");
        assertThat(result.priority()).isEqualTo(TaskPriority.HIGH);
    }

    @Test
    void execute_notFound_throwsTaskNotFoundException() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findByIdAndTenantId(taskId, tenantId)).thenReturn(Optional.empty());

        UpdateTaskCommand command = new UpdateTaskCommand(
                taskId, tenantId, UUID.randomUUID(), "title", null, TaskPriority.LOW, null);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(TaskNotFoundException.class);
    }
}
