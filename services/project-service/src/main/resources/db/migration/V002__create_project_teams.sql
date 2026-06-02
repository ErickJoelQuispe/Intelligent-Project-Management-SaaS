-- Project teams table (team assignments)
CREATE TABLE project_teams (
    id          UUID         NOT NULL,
    project_id  UUID         NOT NULL,
    team_id     UUID         NOT NULL,
    assigned_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    orphaned_at TIMESTAMPTZ  NULL,
    tenant_id   UUID         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(255) NOT NULL DEFAULT 'system',
    updated_by  VARCHAR(255) NOT NULL DEFAULT 'system',
    version     BIGINT       NOT NULL DEFAULT 0,
    deleted_at  TIMESTAMPTZ  NULL,
    CONSTRAINT pk_project_teams PRIMARY KEY (id),
    CONSTRAINT fk_project_teams_project FOREIGN KEY (project_id) REFERENCES projects(id)
);
CREATE UNIQUE INDEX uix_project_teams_active ON project_teams (project_id, team_id) WHERE orphaned_at IS NULL;
CREATE INDEX idx_project_teams_team ON project_teams (team_id) WHERE orphaned_at IS NULL;
