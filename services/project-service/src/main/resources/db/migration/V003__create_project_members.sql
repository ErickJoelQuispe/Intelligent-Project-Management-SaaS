-- Project members table
CREATE TABLE project_members (
    id         UUID        NOT NULL,
    project_id UUID        NOT NULL,
    profile_id UUID        NOT NULL,
    role       VARCHAR(20) NOT NULL CHECK (role IN ('OWNER','MANAGER','CONTRIBUTOR','VIEWER')),
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    removed_at TIMESTAMPTZ NULL,
    tenant_id  UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(255) NOT NULL DEFAULT 'system',
    version    BIGINT      NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ NULL,
    CONSTRAINT pk_project_members PRIMARY KEY (id),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects(id)
);
CREATE UNIQUE INDEX uix_project_members_active ON project_members (project_id, profile_id) WHERE removed_at IS NULL;
CREATE INDEX idx_project_members_profile ON project_members (profile_id, tenant_id);
