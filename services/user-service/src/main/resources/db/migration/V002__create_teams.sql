-- Teams table
CREATE TABLE teams (
    id          UUID         NOT NULL,
    tenant_id   UUID         NOT NULL,
    owner_id    UUID         NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(255) NOT NULL DEFAULT 'system',
    updated_by  VARCHAR(255) NOT NULL DEFAULT 'system',
    version     BIGINT       NOT NULL DEFAULT 0,
    deleted_at  TIMESTAMPTZ  NULL,
    CONSTRAINT pk_teams PRIMARY KEY (id)
);
CREATE INDEX idx_teams_tenant ON teams (tenant_id);
