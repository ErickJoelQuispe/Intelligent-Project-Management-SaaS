package com.epm.task.domain.port.out;

import java.time.LocalDate;
import java.util.UUID;

import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.model.TaskStatus;

/**
 * Read model row from the task_kanban_view materialized view.
 */
public record KanbanTaskRow(
        UUID taskId,
        String title,
        TaskStatus status,
        TaskPriority priority,
        UUID assigneeId,
        LocalDate deadline,
        UUID parentTaskId) {
}
