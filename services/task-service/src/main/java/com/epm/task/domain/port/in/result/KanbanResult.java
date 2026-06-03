package com.epm.task.domain.port.in.result;

import java.util.List;
import java.util.Map;

import com.epm.task.domain.model.TaskStatus;
import com.epm.task.domain.port.out.KanbanTaskRow;

/**
 * Read model returned by {@link com.epm.task.domain.port.in.GetTaskKanbanUseCase}.
 *
 * <p>The columns map contains an entry for all 5 {@link TaskStatus} values,
 * even if the list is empty for some statuses.
 */
public record KanbanResult(Map<TaskStatus, List<KanbanTaskRow>> columns) {
}
