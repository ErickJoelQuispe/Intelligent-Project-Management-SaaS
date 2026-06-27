-- Redefine kanban view to exclude subtasks (parent_task_id IS NULL).
-- Subtasks are shown nested inside their parent card, not as independent columns.
DROP MATERIALIZED VIEW IF EXISTS task_kanban_view;

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
WHERE status != 'CANCELLED'
  AND parent_task_id IS NULL;

CREATE UNIQUE INDEX idx_task_kanban_view ON task_kanban_view (task_id);
CREATE INDEX idx_kanban_view_project_tenant ON task_kanban_view (project_id, tenant_id);
