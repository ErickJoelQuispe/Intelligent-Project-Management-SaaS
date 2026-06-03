package com.epm.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.epm.task.application.usecase.CreateSubtaskUseCaseImpl;
import com.epm.task.domain.exception.MaxDepthExceededException;
import com.epm.task.domain.exception.TaskNotFoundException;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.model.TaskStatus;
import com.epm.task.domain.port.in.command.CreateSubtaskCommand;
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
 * Unit tests for CreateSubtaskUseCaseImpl.
 */
@ExtendWith(MockitoExtension.class)
class CreateSubtaskUseCaseImplTest {

    @Mock TaskRepository taskRepository;
    @Mock ActivityLogRepository activityLogRepository;
    @Mock DomainEventPublisher eventPublisher;

    CreateSubtaskUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateSubtaskUseCaseImpl(taskRepository, activityLogRepository, eventPublisher);
    }

    @Test
    void execute_underRootTask_createsSubtask() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Task rootTask = Task.create(new CreateTaskCommand(
                tenantId, projectId, UUID.randomUUID(), "Root task", null, TaskPriority.MEDIUM, null, null));
        rootTask.pullDomainEvents();

        when(taskRepository.findByIdAndTenantId(rootTask.getId(), tenantId))
                .thenReturn(Optional.of(rootTask));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateSubtaskCommand command = new CreateSubtaskCommand(
                tenantId, projectId, rootTask.getId(), UUID.randomUUID(),
                "Subtask", null, TaskPriority.LOW, null, null);

        TaskResult result = useCase.execute(command);

        assertThat(result.parentTaskId()).isEqualTo(rootTask.getId());
        assertThat(result.status()).isEqualTo(TaskStatus.TODO);
        assertThat(result.title()).isEqualTo("Subtask");
    }

    @Test
    void execute_underSubtask_throwsMaxDepthExceededException() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();

        // Reconstitute a subtask (has a parentTaskId)
        Task subtask = Task.reconstitute(
                UUID.randomUUID(), tenantId, projectId, rootId,
                "Existing subtask", null, TaskStatus.TODO, TaskPriority.LOW,
                null, null, java.time.Instant.now(), java.time.Instant.now());

        when(taskRepository.findByIdAndTenantId(subtask.getId(), tenantId))
                .thenReturn(Optional.of(subtask));

        CreateSubtaskCommand command = new CreateSubtaskCommand(
                tenantId, projectId, subtask.getId(), UUID.randomUUID(),
                "Grandchild", null, TaskPriority.LOW, null, null);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(MaxDepthExceededException.class);
    }

    @Test
    void execute_parentNotFound_throwsTaskNotFoundException() {
        UUID tenantId = UUID.randomUUID();
        UUID nonExistentId = UUID.randomUUID();
        when(taskRepository.findByIdAndTenantId(nonExistentId, tenantId))
                .thenReturn(Optional.empty());

        CreateSubtaskCommand command = new CreateSubtaskCommand(
                tenantId, UUID.randomUUID(), nonExistentId, UUID.randomUUID(),
                "Orphan subtask", null, TaskPriority.MEDIUM, null, null);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(TaskNotFoundException.class);
    }
}
