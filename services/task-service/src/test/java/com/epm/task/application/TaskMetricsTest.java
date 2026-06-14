package com.epm.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.epm.task.application.usecase.CreateTaskUseCaseImpl;
import com.epm.task.domain.model.ActivityLog;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.port.in.command.CreateTaskCommand;
import com.epm.task.domain.port.out.ProjectMembershipPort;
import com.epm.task.domain.port.out.TransactionalOutboxWriter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests verifying that {@link CreateTaskUseCaseImpl} increments the
 * {@code tasks.created} Micrometer counter on each successful task creation.
 *
 * <p>Uses {@link SimpleMeterRegistry} — no Prometheus or Spring context needed.
 */
@ExtendWith(MockitoExtension.class)
class TaskMetricsTest {

    @Mock
    TransactionalOutboxWriter outboxWriter;

    @Mock
    ProjectMembershipPort membershipPort;

    SimpleMeterRegistry meterRegistry;
    CreateTaskUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        useCase = new CreateTaskUseCaseImpl(outboxWriter, membershipPort, meterRegistry);
    }

    @Test
    void createTask_incrementsTasksCreatedCounter() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        CreateTaskCommand command = new CreateTaskCommand(
                tenantId, projectId, callerId, "Implement login", null, TaskPriority.HIGH, null, null);

        when(membershipPort.isMember(projectId, callerId, tenantId)).thenReturn(true);
        when(outboxWriter.saveAndPublish(any(Task.class), any(ActivityLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(command);

        Counter counter = meterRegistry.find("tasks.created")
                .tag("tenantId", tenantId.toString())
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void createTask_twice_counterIsTwo() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        when(membershipPort.isMember(projectId, callerId, tenantId)).thenReturn(true);
        when(outboxWriter.saveAndPublish(any(Task.class), any(ActivityLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CreateTaskCommand cmd1 = new CreateTaskCommand(
                tenantId, projectId, callerId, "Task one", null, TaskPriority.LOW, null, null);
        CreateTaskCommand cmd2 = new CreateTaskCommand(
                tenantId, projectId, callerId, "Task two", null, TaskPriority.MEDIUM, null, null);

        useCase.execute(cmd1);
        useCase.execute(cmd2);

        Counter counter = meterRegistry.find("tasks.created")
                .tag("tenantId", tenantId.toString())
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }
}
