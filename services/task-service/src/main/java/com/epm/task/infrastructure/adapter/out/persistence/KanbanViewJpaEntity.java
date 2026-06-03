package com.epm.task.infrastructure.adapter.out.persistence;

import java.time.LocalDate;
import java.util.UUID;

import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.model.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Read-only JPA entity mapping to the {@code task_kanban_view} materialized view.
 *
 * <p>Used exclusively by {@link KanbanViewPersistenceAdapter} for Kanban reads.
 * Writes always go through {@code tasks} table, not this view.
 */
@Entity
@Table(name = "task_kanban_view")
@org.hibernate.annotations.Immutable
public class KanbanViewJpaEntity {

    @Id
    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private TaskPriority priority;

    @Column(name = "assignee_id", updatable = false)
    private UUID assigneeId;

    @Column(name = "deadline", updatable = false)
    private LocalDate deadline;

    @Column(name = "parent_task_id", updatable = false)
    private UUID parentTaskId;

    // ── Getters ────────────────────────────────────────────────────────────

    public UUID getTaskId() { return taskId; }
    public UUID getProjectId() { return projectId; }
    public UUID getTenantId() { return tenantId; }
    public String getTitle() { return title; }
    public TaskStatus getStatus() { return status; }
    public TaskPriority getPriority() { return priority; }
    public UUID getAssigneeId() { return assigneeId; }
    public LocalDate getDeadline() { return deadline; }
    public UUID getParentTaskId() { return parentTaskId; }
}
