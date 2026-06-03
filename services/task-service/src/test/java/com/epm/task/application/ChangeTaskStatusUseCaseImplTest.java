package com.epm.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.epm.task.application.usecase.ChangeTaskStatusUseCaseImpl;
import com.epm.task.domain.exception.TaskNotFoundException;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.model.TaskStatus;
import com.epm.task.domain.port.in.command.ChangeStatusCommand;
import com.epm.task.domain.port.in.command.CreateTaskCommand;
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
 * Unit tests for ChangeTaskStatusUseCaseImpl.
 */
@ExtendWith(MockitoExtension.class)
class ChangeTaskStatusUseCaseImplTest {

    @Mock TaskRepository taskRepository;
    @Mock ActivityLogRepository activityLogRepository;
    @Mock DomainEventPublisher eventPublisher;

    ChangeTaskStatusUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new ChangeTaskStatusUseCaseImpl(taskRepository, activityLogRepository, eventPublisher);
    }

    @Test
    void execute_existingTask_changesStatusAndPublishesEvent() {
        UUID tenantId = UUID.randomUUID();
        Task task = Task.create(new CreateTaskCommand(
                tenantId, UUID.randomUUID(), UUID.randomUUID(),
                "My task", null, TaskPriority.MEDIUM, null, null));
        task.pullDomainEvents();

        when(taskRepository.findByIdAndTenantId(task.getId(), tenantId))
                .thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChangeStatusCommand command = new ChangeStatusCommand(
                task.getId(), tenantId, UUID.randomUUID(), TaskStatus.IN_PROGRESS);

        TaskResult result = useCase.execute(command);

        assertThat(result.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void execute_notFound_throwsTaskNotFoundException() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findByIdAndTenantId(taskId, tenantId)).thenReturn(Optional.empty());

        ChangeStatusCommand command = new ChangeStatusCommand(
                taskId, tenantId, UUID.randomUUID(), TaskStatus.DONE);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(TaskNotFoundException.class);
    }
}
