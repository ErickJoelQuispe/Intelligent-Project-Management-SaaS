package com.epm.task.infrastructure.adapter.in.web;

import java.util.List;
import java.util.Map;

import com.epm.task.domain.model.TaskStatus;

/**
 * Response DTO for the Kanban board endpoint.
 *
 * <p>Contains one key per {@link TaskStatus}, mapping to a list of task summaries.
 */
public record KanbanResponse(Map<String, List<KanbanTaskSummary>> columns) {
}
