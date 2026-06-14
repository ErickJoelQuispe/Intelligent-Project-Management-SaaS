package com.epm.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.epm.task.application.usecase.CreateTaskUseCaseImpl;
import com.epm.task.domain.exception.ProjectMembershipRequiredException;
import com.epm.task.domain.model.ActivityLog;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.model.TaskStatus;
import com.epm.task.domain.port.in.command.CreateTaskCommand;
import com.epm.task.domain.port.in.result.TaskResult;
import com.epm.task.domain.port.out.ProjectMembershipPort;
import com.epm.task.domain.port.out.TransactionalOutboxWriter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for CreateTaskUseCaseImpl.
 */
@ExtendWith(MockitoExtension.class)
class CreateTaskUseCaseImplTest {

    @Mock TransactionalOutboxWriter outboxWriter;
    @Mock ProjectMembershipPort membershipPort;

    CreateTaskUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateTaskUseCaseImpl(outboxWriter, membershipPort, new SimpleMeterRegistry());
    }

    @Test
    void execute_whenMember_createsTaskAndPublishesEvent() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        CreateTaskCommand command = new CreateTaskCommand(
                tenantId, projectId, callerId, "Build login page", null, TaskPriority.HIGH, null, null);

        when(membershipPort.isMember(projectId, callerId, tenantId)).thenReturn(true);
        when(outboxWriter.saveAndPublish(any(Task.class), any(ActivityLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TaskResult result = useCase.execute(command);

        assertThat(result.status()).isEqualTo(TaskStatus.TODO);
        assertThat(result.title()).isEqualTo("Build login page");
        assertThat(result.tenantId()).isEqualTo(tenantId);
        assertThat(result.projectId()).isEqualTo(projectId);
        assertThat(result.parentTaskId()).isNull();
        verify(outboxWriter).saveAndPublish(any(Task.class), any(ActivityLog.class));
    }

    @Test
    void execute_whenNotMember_throwsProjectMembershipRequiredException() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        CreateTaskCommand command = new CreateTaskCommand(
                tenantId, projectId, callerId, "Some task", null, TaskPriority.LOW, null, null);

        when(membershipPort.isMember(projectId, callerId, tenantId)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(ProjectMembershipRequiredException.class);
    }
}
