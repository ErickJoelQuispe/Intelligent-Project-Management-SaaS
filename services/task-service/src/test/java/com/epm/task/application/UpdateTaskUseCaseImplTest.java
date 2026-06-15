package com.epm.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.epm.task.application.usecase.UpdateTaskUseCaseImpl;
import com.epm.task.domain.exception.TaskNotFoundException;
import com.epm.task.domain.model.ActivityLog;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.port.in.command.CreateTaskCommand;
import com.epm.task.domain.port.in.command.UpdateTaskCommand;
import com.epm.task.domain.port.in.result.TaskResult;
import com.epm.task.domain.port.out.ProjectMembershipPort;
import com.epm.task.domain.port.out.TaskRepository;
import com.epm.task.domain.port.out.TransactionalOutboxWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for UpdateTaskUseCaseImpl.
 */
@ExtendWith(MockitoExtension.class)
class UpdateTaskUseCaseImplTest {

    @Mock TaskRepository taskRepository;
    @Mock TransactionalOutboxWriter outboxWriter;
    @Mock ProjectMembershipPort membershipPort;

    UpdateTaskUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateTaskUseCaseImpl(taskRepository, outboxWriter, membershipPort);
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
        when(membershipPort.isMember(any(), any(), any())).thenReturn(true);
        when(outboxWriter.saveAndPublish(any(Task.class), any(ActivityLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UpdateTaskCommand command = new UpdateTaskCommand(
                task.getId(), tenantId, UUID.randomUUID(),
                "New title", "Description updated", TaskPriority.HIGH, null);

        TaskResult result = useCase.execute(command);

        assertThat(result.title()).isEqualTo("New title");
        assertThat(result.description()).isEqualTo("Description updated");
        assertThat(result.priority()).isEqualTo(TaskPriority.HIGH);
        verify(outboxWriter).saveAndPublish(any(Task.class), any(ActivityLog.class));
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
