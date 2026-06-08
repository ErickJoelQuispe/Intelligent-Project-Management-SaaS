package com.epm.task.domain.port.out;

import java.util.List;
import java.util.UUID;

/**
 * Driven port for reading the Kanban view (backed by the task_kanban_view materialized view).
 */
public interface KanbanViewRepository {

    /**
     * Returns all Kanban task rows for the given project and tenant.
     *
     * @param projectId the project to query
     * @param tenantId  tenant scope
     * @return list of Kanban task rows (may be empty)
     */
    List<KanbanTaskRow> findKanbanByProjectIdAndTenantId(UUID projectId, UUID tenantId);
}
