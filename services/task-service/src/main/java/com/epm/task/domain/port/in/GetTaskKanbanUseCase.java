package com.epm.task.domain.port.in;

import java.util.UUID;

import com.epm.task.domain.port.in.result.KanbanResult;

/**
 * Driving port: retrieves tasks grouped by status for the Kanban board.
 */
public interface GetTaskKanbanUseCase {

    KanbanResult execute(UUID projectId, UUID tenantId);
}
