-- Team memberships table
CREATE TABLE team_memberships (
    id         UUID        NOT NULL,
    team_id    UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    role       VARCHAR(20) NOT NULL CHECK (role IN ('OWNER','MEMBER','VIEWER')),
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    removed_at TIMESTAMPTZ NULL,
    tenant_id  UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(255) NOT NULL DEFAULT 'system',
    version    BIGINT      NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ NULL,
    CONSTRAINT pk_team_memberships PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uix_team_memberships_active
    ON team_memberships (team_id, user_id) WHERE removed_at IS NULL;
CREATE INDEX idx_team_memberships_user ON team_memberships (user_id, tenant_id);
