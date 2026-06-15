package com.epm.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.epm.task.application.usecase.AssignTaskUseCaseImpl;
import com.epm.task.domain.exception.TaskNotFoundException;
import com.epm.task.domain.model.ActivityLog;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.model.TaskStatus;
import com.epm.task.domain.port.in.command.AssignTaskCommand;
import com.epm.task.domain.port.in.command.CreateTaskCommand;
import com.epm.task.domain.port.in.result.TaskResult;
import com.epm.task.domain.port.out.ProjectMembershipPort;
import com.epm.task.domain.port.out.TaskRepository;
import com.epm.task.domain.port.out.TransactionalOutboxWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for AssignTaskUseCaseImpl.
 */
@ExtendWith(MockitoExtension.class)
class AssignTaskUseCaseImplTest {

    @Mock TaskRepository taskRepository;
    @Mock TransactionalOutboxWriter outboxWriter;
    @Mock ProjectMembershipPort membershipPort;

    AssignTaskUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new AssignTaskUseCaseImpl(taskRepository, outboxWriter, membershipPort);
    }

    @Test
    void execute_existingTask_setsAssigneeAndPublishesEvent() {
        UUID tenantId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        Task task = Task.create(new CreateTaskCommand(
                tenantId, UUID.randomUUID(), UUID.randomUUID(),
                "Task to assign", null, TaskPriority.HIGH, null, null));
        task.pullDomainEvents();

        when(taskRepository.findByIdAndTenantId(task.getId(), tenantId))
                .thenReturn(Optional.of(task));
        when(membershipPort.isMember(any(), any(), any())).thenReturn(true);
        when(outboxWriter.saveAndPublish(any(Task.class), any(ActivityLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AssignTaskCommand command = new AssignTaskCommand(task.getId(), tenantId, UUID.randomUUID(), assigneeId);

        TaskResult result = useCase.execute(command);

        assertThat(result.assigneeId()).isEqualTo(assigneeId);
        verify(outboxWriter).saveAndPublish(any(Task.class), any(ActivityLog.class));
    }

    @Test
    void execute_assigneeNull_clearsAssignee() {
        UUID tenantId = UUID.randomUUID();
        UUID existingAssignee = UUID.randomUUID();
        Task task = Task.reconstitute(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), null,
                "Assigned task", null,
                TaskStatus.TODO, TaskPriority.MEDIUM,
                null, existingAssignee, Instant.now(), Instant.now());

        when(taskRepository.findByIdAndTenantId(task.getId(), tenantId))
                .thenReturn(Optional.of(task));
        when(membershipPort.isMember(any(), any(), any())).thenReturn(true);
        when(outboxWriter.saveAndPublish(any(Task.class), any(ActivityLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AssignTaskCommand command = new AssignTaskCommand(task.getId(), tenantId, UUID.randomUUID(), null);

        TaskResult result = useCase.execute(command);

        assertThat(result.assigneeId()).isNull();
    }

    @Test
    void execute_notFound_throwsTaskNotFoundException() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findByIdAndTenantId(taskId, tenantId)).thenReturn(Optional.empty());

        AssignTaskCommand command = new AssignTaskCommand(taskId, tenantId, UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(TaskNotFoundException.class);
    }
}
