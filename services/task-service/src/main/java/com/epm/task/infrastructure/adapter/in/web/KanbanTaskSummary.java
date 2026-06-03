package com.epm.task.infrastructure.adapter.in.web;

import java.time.LocalDate;
import java.util.UUID;

import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.model.TaskStatus;
import com.epm.task.domain.port.out.KanbanTaskRow;

/**
 * Summary DTO for a single task card in the Kanban board.
 */
public record KanbanTaskSummary(
        UUID taskId,
        String title,
        TaskStatus status,
        TaskPriority priority,
        UUID assigneeId,
        LocalDate deadline,
        UUID parentTaskId) {

    public static KanbanTaskSummary from(KanbanTaskRow row) {
        return new KanbanTaskSummary(
                row.taskId(),
                row.title(),
                row.status(),
                row.priority(),
                row.assigneeId(),
                row.deadline(),
                row.parentTaskId());
    }
}
