package com.epm.task.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.epm.task.domain.event.TaskAssigned;
import com.epm.task.domain.event.TaskCreated;
import com.epm.task.domain.event.TaskDeleted;
import com.epm.task.domain.event.TaskStatusChanged;
import com.epm.task.domain.event.TaskUpdated;
import com.epm.task.domain.exception.TenantRequiredException;
import com.epm.task.domain.port.in.command.CreateTaskCommand;
import com.github.f4b6a3.uuid.UuidCreator;

/**
 * Task aggregate root.
 *
 * <p>A task belongs to a tenant and project. It can have an optional parent task
 * (subtask scenario, max depth = 2 levels).
 *
 * <p>No Spring or JPA annotations — pure Java domain object.
 */
public class Task {

    private final UUID id;
    private final UUID tenantId;
    private final UUID projectId;
    private final UUID parentTaskId;
    private String title;
    private String description;
    private TaskStatus status;
    private TaskPriority priority;
    private LocalDate deadline;
    private UUID assigneeId;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<Object> domainEvents = new ArrayList<>();

    // ── Factory ──────────────────────────────────────────────────────────────

    /**
     * Creates a new root task. Status is set to {@link TaskStatus#TODO}.
     * Records a {@link TaskCreated} domain event.
     *
     * @param command the creation command
     * @return a new Task aggregate
     * @throws TenantRequiredException   if tenantId is null
     * @throws IllegalArgumentException  if title is blank or exceeds 255 characters
     */
    public static Task create(CreateTaskCommand command) {
        guardTenantId(command.tenantId());
        guardTitle(command.title());
        Instant now = Instant.now();
        UUID taskId = UuidCreator.getTimeOrderedEpoch();
        Task task = new Task(
                taskId,
                command.tenantId(),
                command.projectId(),
                null,
                command.title(),
                command.description(),
                TaskStatus.TODO,
                command.priority(),
                command.deadline(),
                command.assigneeId(),
                now,
                now);
        task.domainEvents.add(new TaskCreated(
                UuidCreator.getTimeOrderedEpoch(),
                taskId,
                command.projectId(),
                command.title(),
                command.tenantId(),
                command.callerId(),
                now));
        return task;
    }

    /**
     * Creates a subtask under a parent. Status is set to {@link TaskStatus#TODO}.
     * Records a {@link TaskCreated} domain event.
     *
     * @param command      the creation command
     * @param parentTaskId the id of the parent task
     * @return a new Task with the given parent
     * @throws TenantRequiredException  if tenantId is null
     * @throws IllegalArgumentException if title is blank or exceeds 255 characters
     */
    public static Task createSubtask(CreateTaskCommand command, UUID parentTaskId) {
        guardTenantId(command.tenantId());
        guardTitle(command.title());
        Instant now = Instant.now();
        UUID taskId = UuidCreator.getTimeOrderedEpoch();
        Task task = new Task(
                taskId,
                command.tenantId(),
                command.projectId(),
                parentTaskId,
                command.title(),
                command.description(),
                TaskStatus.TODO,
                command.priority(),
                command.deadline(),
                command.assigneeId(),
                now,
                now);
        task.domainEvents.add(new TaskCreated(
                UuidCreator.getTimeOrderedEpoch(),
                taskId,
                command.projectId(),
                command.title(),
                command.tenantId(),
                command.callerId(),
                now));
        return task;
    }

    /**
     * Reconstitutes a Task from persistence (no domain events raised).
     */
    public static Task reconstitute(UUID id, UUID tenantId, UUID projectId,
            UUID parentTaskId, String title, String description,
            TaskStatus status, TaskPriority priority, LocalDate deadline,
            UUID assigneeId, Instant createdAt, Instant updatedAt) {
        return new Task(id, tenantId, projectId, parentTaskId, title, description,
                status, priority, deadline, assigneeId, createdAt, updatedAt);
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    private Task(UUID id, UUID tenantId, UUID projectId, UUID parentTaskId,
            String title, String description, TaskStatus status, TaskPriority priority,
            LocalDate deadline, UUID assigneeId, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.parentTaskId = parentTaskId;
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.deadline = deadline;
        this.assigneeId = assigneeId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ── Business methods ─────────────────────────────────────────────────────

    /**
     * Updates the task's mutable fields and records a {@link TaskUpdated} event.
     *
     * @param title       new title (1–255 chars)
     * @param description new description (nullable)
     * @param priority    new priority
     * @param deadline    new deadline (nullable)
     * @throws IllegalArgumentException if title is blank or exceeds 255 characters
     */
    public void update(String title, String description, TaskPriority priority, LocalDate deadline) {
        guardTitle(title);
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.deadline = deadline;
        this.updatedAt = Instant.now();
        domainEvents.add(new TaskUpdated(
                UuidCreator.getTimeOrderedEpoch(),
                this.id,
                this.tenantId,
                this.updatedAt));
    }

    /**
     * Changes the task status and records a {@link TaskStatusChanged} event.
     *
     * @param newStatus the new status
     */
    public void changeStatus(TaskStatus newStatus) {
        TaskStatus oldStatus = this.status;
        this.status = newStatus;
        this.updatedAt = Instant.now();
        domainEvents.add(new TaskStatusChanged(
                UuidCreator.getTimeOrderedEpoch(),
                this.id,
                oldStatus,
                newStatus,
                this.tenantId,
                this.updatedAt));
    }

    /**
     * Assigns the task to a user and records a {@link TaskAssigned} event.
     *
     * @param assigneeId the user to assign the task to (nullable — clears assignee)
     */
    public void assign(UUID assigneeId) {
        this.assigneeId = assigneeId;
        this.updatedAt = Instant.now();
        domainEvents.add(new TaskAssigned(
                UuidCreator.getTimeOrderedEpoch(),
                this.id,
                assigneeId,
                this.tenantId,
                this.updatedAt));
    }

    /**
     * Cancels the task by setting status to {@link TaskStatus#CANCELLED}
     * and records a {@link TaskStatusChanged} event.
     */
    public void cancel() {
        changeStatus(TaskStatus.CANCELLED);
    }

    /**
     * Records a {@link TaskDeleted} event. Called before deletion.
     */
    public void markDeleted() {
        Instant now = Instant.now();
        domainEvents.add(new TaskDeleted(
                UuidCreator.getTimeOrderedEpoch(),
                this.id,
                this.projectId,
                this.tenantId,
                now));
    }

    /**
     * Returns all pending domain events and clears the internal list.
     */
    public List<Object> pullDomainEvents() {
        List<Object> copy = new ArrayList<>(domainEvents);
        domainEvents.clear();
        return copy;
    }

    // ── Private guards ────────────────────────────────────────────────────────

    private static void guardTenantId(UUID tenantId) {
        if (tenantId == null) {
            throw new TenantRequiredException();
        }
    }

    private static void guardTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Task title must not be blank");
        }
        if (title.length() > 255) {
            throw new IllegalArgumentException("Task title must not exceed 255 characters");
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getId() { return id; }

    public UUID getTenantId() { return tenantId; }

    public UUID getProjectId() { return projectId; }

    public UUID getParentTaskId() { return parentTaskId; }

    public String getTitle() { return title; }

    public String getDescription() { return description; }

    public TaskStatus getStatus() { return status; }

    public TaskPriority getPriority() { return priority; }

    public LocalDate getDeadline() { return deadline; }

    public UUID getAssigneeId() { return assigneeId; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    public List<Object> getDomainEvents() { return Collections.unmodifiableList(domainEvents); }
}
