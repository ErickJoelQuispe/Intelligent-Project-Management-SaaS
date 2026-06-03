-- Kanban materialized view (CQRS-light read model)
-- Excludes CANCELLED tasks from the board view.
-- REFRESH MATERIALIZED VIEW CONCURRENTLY requires a unique index.
CREATE MATERIALIZED VIEW task_kanban_view AS
SELECT
    id          AS task_id,
    project_id,
    tenant_id,
    title,
    status,
    priority,
    assignee_id,
    deadline,
    parent_task_id
FROM tasks
WHERE status != 'CANCELLED';

CREATE UNIQUE INDEX idx_task_kanban_view ON task_kanban_view (task_id);
CREATE INDEX idx_kanban_view_project_tenant ON task_kanban_view (project_id, tenant_id);
