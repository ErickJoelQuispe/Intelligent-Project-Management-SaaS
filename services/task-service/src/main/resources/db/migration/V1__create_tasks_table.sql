-- Tasks table
CREATE TABLE tasks (
    id             UUID         NOT NULL,
    tenant_id      UUID         NOT NULL,
    project_id     UUID         NOT NULL,
    parent_task_id UUID         NULL REFERENCES tasks(id),
    title          VARCHAR(255) NOT NULL,
    description    TEXT         NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'TODO'
                                CHECK (status IN ('TODO','IN_PROGRESS','IN_REVIEW','DONE','CANCELLED')),
    priority       VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM'
                                CHECK (priority IN ('HIGH','MEDIUM','LOW')),
    deadline       DATE         NULL,
    assignee_id    UUID         NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_tasks PRIMARY KEY (id)
);

CREATE INDEX idx_tasks_tenant ON tasks (tenant_id);
CREATE INDEX idx_tasks_project ON tasks (project_id);
CREATE INDEX idx_tasks_tenant_project ON tasks (tenant_id, project_id);
CREATE INDEX idx_tasks_project_status ON tasks (project_id, status);
CREATE INDEX idx_tasks_parent ON tasks (parent_task_id);
