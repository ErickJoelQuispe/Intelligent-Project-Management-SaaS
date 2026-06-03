package com.epm.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import com.epm.task.domain.event.TaskAssigned;
import com.epm.task.domain.event.TaskCreated;
import com.epm.task.domain.event.TaskStatusChanged;
import com.epm.task.domain.event.TaskUpdated;
import com.epm.task.domain.exception.TenantRequiredException;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.model.TaskStatus;
import com.epm.task.domain.port.in.command.CreateTaskCommand;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Task aggregate root.
 */
class TaskTest {

    // ── T-B1-10: create() sets status=TODO, parentTaskId=null, records TaskCreated ──

    @Test
    void create_setsStatusTodo_parentTaskIdNull_recordsTaskCreatedEvent() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        CreateTaskCommand command = new CreateTaskCommand(
                tenantId, projectId, callerId, "Implement login", null, TaskPriority.HIGH, null, null);

        Task task = Task.create(command);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(task.getParentTaskId()).isNull();
        assertThat(task.getId()).isNotNull();
        assertThat(task.getTenantId()).isEqualTo(tenantId);
        assertThat(task.getProjectId()).isEqualTo(projectId);
        assertThat(task.getTitle()).isEqualTo("Implement login");

        List<Object> events = task.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TaskCreated.class);
        TaskCreated event = (TaskCreated) events.get(0);
        assertThat(event.taskId()).isEqualTo(task.getId());
        assertThat(event.tenantId()).isEqualTo(tenantId);
        assertThat(event.projectId()).isEqualTo(projectId);
        assertThat(event.title()).isEqualTo("Implement login");
    }

    @Test
    void create_withDifferentPriority_setsCorrectPriority() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        CreateTaskCommand command = new CreateTaskCommand(
                tenantId, projectId, callerId, "Write docs", null, TaskPriority.LOW, null, null);

        Task task = Task.create(command);

        assertThat(task.getPriority()).isEqualTo(TaskPriority.LOW);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
    }

    // ── T-B1-11: create() with null tenantId throws TenantRequiredException ──

    @Test
    void create_withNullTenantId_throwsTenantRequiredException() {
        CreateTaskCommand command = new CreateTaskCommand(
                null, UUID.randomUUID(), UUID.randomUUID(), "My task", null, TaskPriority.MEDIUM, null, null);

        assertThatThrownBy(() -> Task.create(command))
                .isInstanceOf(TenantRequiredException.class);
    }

    @Test
    void create_withBlankTitle_throwsIllegalArgumentException() {
        CreateTaskCommand command = new CreateTaskCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "  ", null, TaskPriority.MEDIUM, null, null);

        assertThatThrownBy(() -> Task.create(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    // ── T-B1-12: changeStatus() records TaskStatusChanged event ──

    @Test
    void changeStatus_recordsTaskStatusChangedEvent() {
        Task task = createValidTask();

        task.changeStatus(TaskStatus.IN_PROGRESS);

        List<Object> events = task.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TaskStatusChanged.class);
        TaskStatusChanged event = (TaskStatusChanged) events.get(0);
        assertThat(event.oldStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(event.newStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(event.taskId()).isEqualTo(task.getId());
    }

    @Test
    void changeStatus_updatesStatusField() {
        Task task = createValidTask();

        task.changeStatus(TaskStatus.DONE);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
    }

    // ── T-B1-13: assign() records TaskAssigned event ──

    @Test
    void assign_recordsTaskAssignedEvent() {
        Task task = createValidTask();
        UUID assigneeId = UUID.randomUUID();

        task.assign(assigneeId);

        List<Object> events = task.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TaskAssigned.class);
        TaskAssigned event = (TaskAssigned) events.get(0);
        assertThat(event.assigneeId()).isEqualTo(assigneeId);
        assertThat(event.taskId()).isEqualTo(task.getId());
    }

    @Test
    void assign_setsAssigneeId() {
        Task task = createValidTask();
        UUID assigneeId = UUID.randomUUID();

        task.assign(assigneeId);

        assertThat(task.getAssigneeId()).isEqualTo(assigneeId);
    }

    // ── T-B1-14: update() records TaskUpdated event ──

    @Test
    void update_recordsTaskUpdatedEvent() {
        Task task = createValidTask();

        task.update("Updated title", "Some description", TaskPriority.LOW, null);

        List<Object> events = task.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TaskUpdated.class);
        TaskUpdated event = (TaskUpdated) events.get(0);
        assertThat(event.taskId()).isEqualTo(task.getId());
        assertThat(event.tenantId()).isEqualTo(task.getTenantId());
    }

    @Test
    void update_changesFields() {
        Task task = createValidTask();

        task.update("New title", "New desc", TaskPriority.HIGH, null);

        assertThat(task.getTitle()).isEqualTo("New title");
        assertThat(task.getDescription()).isEqualTo("New desc");
        assertThat(task.getPriority()).isEqualTo(TaskPriority.HIGH);
    }

    // ── T-B1-15: cancel() sets CANCELLED and records TaskStatusChanged ──

    @Test
    void cancel_setsStatusCancelled_recordsTaskStatusChangedEvent() {
        Task task = createValidTask();

        task.cancel();

        assertThat(task.getStatus()).isEqualTo(TaskStatus.CANCELLED);
        List<Object> events = task.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TaskStatusChanged.class);
        TaskStatusChanged event = (TaskStatusChanged) events.get(0);
        assertThat(event.newStatus()).isEqualTo(TaskStatus.CANCELLED);
    }

    @Test
    void cancel_fromInProgress_recordsOldStatus() {
        Task task = createValidTask();
        task.changeStatus(TaskStatus.IN_PROGRESS);
        task.pullDomainEvents(); // clear create+change events

        task.cancel();

        List<Object> events = task.pullDomainEvents();
        TaskStatusChanged event = (TaskStatusChanged) events.get(0);
        assertThat(event.oldStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(event.newStatus()).isEqualTo(TaskStatus.CANCELLED);
    }

    // ── T-B1-17: pullDomainEvents() clears list after pull ──

    @Test
    void pullDomainEvents_clearsListAfterPull() {
        // Create a fresh task — it records exactly 1 TaskCreated event
        CreateTaskCommand command = new CreateTaskCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Clear test task", null, TaskPriority.MEDIUM, null, null);
        Task task = Task.create(command);

        List<Object> firstPull = task.pullDomainEvents();
        List<Object> secondPull = task.pullDomainEvents();

        assertThat(firstPull).hasSize(1);
        assertThat(secondPull).isEmpty();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Task createValidTask() {
        CreateTaskCommand command = new CreateTaskCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Valid Task", null, TaskPriority.MEDIUM, null, null);
        Task task = Task.create(command);
        task.pullDomainEvents(); // start fresh
        return task;
    }
}
