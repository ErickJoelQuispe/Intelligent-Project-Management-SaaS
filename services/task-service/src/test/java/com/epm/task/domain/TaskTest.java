package com.epm.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import com.epm.task.domain.event.TaskAssigned;
import com.epm.task.domain.event.TaskCreated;
import com.epm.task.domain.event.TaskStatusChanged;
import com.epm.task.domain.event.TaskUpdated;
import com.epm.task.domain.exception.InvalidStatusException;
import com.epm.task.domain.exception.TenantRequiredException;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.model.TaskStatus;
import com.epm.task.domain.port.in.command.CreateTaskCommand;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Task aggregate root.
 *
 * <p>Covers: factory methods, FSM transitions (allowed, disallowed, same-state no-op),
 * cancel() exempt from FSM, assign(), update(), pullDomainEvents().
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

    // ── FSM: allowed transitions ───────────────────────────────────────────────

    @Test
    void changeStatus_todoToInProgress_allowed() {
        Task task = createValidTask(); // TODO
        task.changeStatus(TaskStatus.IN_PROGRESS);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(task.getDomainEvents()).hasSize(1);
    }

    @Test
    void changeStatus_todoToCancelled_allowed() {
        Task task = createValidTask(); // TODO
        task.changeStatus(TaskStatus.CANCELLED);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.CANCELLED);
    }

    @Test
    void changeStatus_inProgressToTodo_allowed() {
        Task task = createTaskWithStatus(TaskStatus.IN_PROGRESS);
        task.changeStatus(TaskStatus.TODO);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void changeStatus_inProgressToInReview_allowed() {
        Task task = createTaskWithStatus(TaskStatus.IN_PROGRESS);
        task.changeStatus(TaskStatus.IN_REVIEW);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_REVIEW);
    }

    @Test
    void changeStatus_inProgressToDone_allowed() {
        Task task = createTaskWithStatus(TaskStatus.IN_PROGRESS);
        task.changeStatus(TaskStatus.DONE);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void changeStatus_inProgressToCancelled_allowed() {
        Task task = createTaskWithStatus(TaskStatus.IN_PROGRESS);
        task.changeStatus(TaskStatus.CANCELLED);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.CANCELLED);
    }

    @Test
    void changeStatus_inReviewToInProgress_allowed() {
        Task task = createTaskWithStatus(TaskStatus.IN_REVIEW);
        task.changeStatus(TaskStatus.IN_PROGRESS);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void changeStatus_inReviewToDone_allowed() {
        Task task = createTaskWithStatus(TaskStatus.IN_REVIEW);
        task.changeStatus(TaskStatus.DONE);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void changeStatus_inReviewToCancelled_allowed() {
        Task task = createTaskWithStatus(TaskStatus.IN_REVIEW);
        task.changeStatus(TaskStatus.CANCELLED);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.CANCELLED);
    }

    @Test
    void changeStatus_doneToInProgress_allowed_reopen() {
        Task task = createTaskWithStatus(TaskStatus.DONE);
        task.changeStatus(TaskStatus.IN_PROGRESS);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void changeStatus_cancelledToTodo_allowed_reactivate() {
        Task task = createTaskWithStatus(TaskStatus.CANCELLED);
        task.changeStatus(TaskStatus.TODO);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
    }

    // ── FSM: disallowed transitions ────────────────────────────────────────────

    @Test
    void changeStatus_todoToDone_throws() {
        Task task = createValidTask(); // TODO
        assertThatThrownBy(() -> task.changeStatus(TaskStatus.DONE))
                .isInstanceOf(InvalidStatusException.class)
                .hasMessageContaining("TODO")
                .hasMessageContaining("DONE");
    }

    @Test
    void changeStatus_todoToInReview_throws() {
        Task task = createValidTask(); // TODO
        assertThatThrownBy(() -> task.changeStatus(TaskStatus.IN_REVIEW))
                .isInstanceOf(InvalidStatusException.class);
    }

    @Test
    void changeStatus_doneToCancelled_throws() {
        Task task = createTaskWithStatus(TaskStatus.DONE);
        assertThatThrownBy(() -> task.changeStatus(TaskStatus.CANCELLED))
                .isInstanceOf(InvalidStatusException.class)
                .hasMessageContaining("DONE")
                .hasMessageContaining("CANCELLED");
    }

    @Test
    void changeStatus_doneToTodo_throws() {
        Task task = createTaskWithStatus(TaskStatus.DONE);
        assertThatThrownBy(() -> task.changeStatus(TaskStatus.TODO))
                .isInstanceOf(InvalidStatusException.class);
    }

    @Test
    void changeStatus_cancelledToDone_throws() {
        Task task = createTaskWithStatus(TaskStatus.CANCELLED);
        assertThatThrownBy(() -> task.changeStatus(TaskStatus.DONE))
                .isInstanceOf(InvalidStatusException.class);
    }

    @Test
    void changeStatus_cancelledToInProgress_throws() {
        Task task = createTaskWithStatus(TaskStatus.CANCELLED);
        assertThatThrownBy(() -> task.changeStatus(TaskStatus.IN_PROGRESS))
                .isInstanceOf(InvalidStatusException.class);
    }

    // ── FSM: same-state no-op ──────────────────────────────────────────────────

    @Test
    void changeStatus_sameState_todoToTodo_isNoOp() {
        Task task = createValidTask(); // TODO
        task.changeStatus(TaskStatus.TODO);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(task.getDomainEvents()).isEmpty(); // no event emitted
    }

    @Test
    void changeStatus_sameState_inProgressToInProgress_isNoOp() {
        Task task = createTaskWithStatus(TaskStatus.IN_PROGRESS);
        task.changeStatus(TaskStatus.IN_PROGRESS);
        assertThat(task.getDomainEvents()).isEmpty();
    }

    @Test
    void changeStatus_sameState_doneToSameDone_isNoOp() {
        Task task = createTaskWithStatus(TaskStatus.DONE);
        task.changeStatus(TaskStatus.DONE);
        assertThat(task.getDomainEvents()).isEmpty();
    }

    // ── cancel(): system operation — exempt from FSM ───────────────────────────

    @Test
    void cancel_fromTodo_setsCANCELLED_emitsEvent() {
        Task task = createValidTask(); // TODO
        task.cancel();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.CANCELLED);
        List<Object> events = task.pullDomainEvents();
        assertThat(events).hasSize(1);
        TaskStatusChanged event = (TaskStatusChanged) events.get(0);
        assertThat(event.oldStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(event.newStatus()).isEqualTo(TaskStatus.CANCELLED);
    }

    @Test
    void cancel_fromInProgress_setsCANCELLED_emitsEvent() {
        Task task = createTaskWithStatus(TaskStatus.IN_PROGRESS);
        task.cancel();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.CANCELLED);
        List<Object> events = task.pullDomainEvents();
        assertThat(events).hasSize(1);
        TaskStatusChanged event = (TaskStatusChanged) events.get(0);
        assertThat(event.oldStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void cancel_fromDone_setsCANCELLED_exemptFromFsm() {
        // cancel() is the cascade/system op — DONE → CANCELLED is allowed
        Task task = createTaskWithStatus(TaskStatus.DONE);
        task.cancel();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.CANCELLED);
        List<Object> events = task.pullDomainEvents();
        assertThat(events).hasSize(1);
        TaskStatusChanged event = (TaskStatusChanged) events.get(0);
        assertThat(event.oldStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(event.newStatus()).isEqualTo(TaskStatus.CANCELLED);
    }

    @Test
    void cancel_fromInReview_setsCANCELLED_emitsEvent() {
        Task task = createTaskWithStatus(TaskStatus.IN_REVIEW);
        task.cancel();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.CANCELLED);
    }

    @Test
    void cancel_alreadyCancelled_isNoOp() {
        Task task = createTaskWithStatus(TaskStatus.CANCELLED);
        task.cancel();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(task.getDomainEvents()).isEmpty(); // no event emitted
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

        task.changeStatus(TaskStatus.IN_PROGRESS);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
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

    /**
     * Creates a task reconstituted at the given status (simulates a loaded-from-DB task).
     * Uses {@code Task.reconstitute} so no domain events are emitted.
     */
    private static Task createTaskWithStatus(TaskStatus status) {
        return Task.reconstitute(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "Task in status " + status,
                null,
                status,
                TaskPriority.MEDIUM,
                null,
                null,
                java.time.Instant.now(),
                java.time.Instant.now());
    }
}
