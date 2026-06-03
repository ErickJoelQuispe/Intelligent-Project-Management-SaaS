package com.epm.task.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.epm.task.application.usecase.DeleteTaskUseCaseImpl;
import com.epm.task.domain.exception.TaskNotFoundException;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.model.TaskStatus;
import com.epm.task.domain.port.in.command.CreateTaskCommand;
import com.epm.task.domain.port.out.ActivityLogRepository;
import com.epm.task.domain.port.out.DomainEventPublisher;
import com.epm.task.domain.port.out.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for DeleteTaskUseCaseImpl.
 */
@ExtendWith(MockitoExtension.class)
class DeleteTaskUseCaseImplTest {

    @Mock TaskRepository taskRepository;
    @Mock ActivityLogRepository activityLogRepository;
    @Mock DomainEventPublisher eventPublisher;

    DeleteTaskUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeleteTaskUseCaseImpl(taskRepository, activityLogRepository, eventPublisher);
    }

    @Test
    void execute_rootTaskWithSubtasks_cancelsSubtasksAndDeletesRoot() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Task rootTask = Task.create(new CreateTaskCommand(
                tenantId, projectId, UUID.randomUUID(), "Root", null, TaskPriority.HIGH, null, null));
        rootTask.pullDomainEvents();

        Task subtask1 = Task.reconstitute(
                UUID.randomUUID(), tenantId, projectId, rootTask.getId(),
                "Subtask 1", null, TaskStatus.TODO, TaskPriority.LOW,
                null, null, Instant.now(), Instant.now());
        Task subtask2 = Task.reconstitute(
                UUID.randomUUID(), tenantId, projectId, rootTask.getId(),
                "Subtask 2", null, TaskStatus.IN_PROGRESS, TaskPriority.MEDIUM,
                null, null, Instant.now(), Instant.now());

        when(taskRepository.findByIdAndTenantId(rootTask.getId(), tenantId))
                .thenReturn(Optional.of(rootTask));
        when(taskRepository.findSubtasksByParentId(rootTask.getId(), tenantId))
                .thenReturn(List.of(subtask1, subtask2));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(rootTask.getId(), tenantId);

        // Both subtasks should be saved (cancelled)
        verify(taskRepository, times(2)).save(any(Task.class));
        // Root should be deleted
        verify(taskRepository).deleteByIdAndTenantId(rootTask.getId(), tenantId);
        // Events published: 2 TaskStatusChanged + 1 TaskDeleted
        verify(eventPublisher, times(3)).publish(any());
    }

    @Test
    void execute_notFound_throwsTaskNotFoundException() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findByIdAndTenantId(taskId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(taskId, tenantId))
                .isInstanceOf(TaskNotFoundException.class);
    }
}
