-- Projects table
CREATE TABLE projects (
    id          UUID         NOT NULL,
    tenant_id   UUID         NOT NULL,
    owner_id    UUID         NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','COMPLETED','ARCHIVED')),
    visibility  VARCHAR(20)  NOT NULL DEFAULT 'PRIVATE' CHECK (visibility IN ('PRIVATE','TEAM','PUBLIC')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(255) NOT NULL DEFAULT 'system',
    updated_by  VARCHAR(255) NOT NULL DEFAULT 'system',
    version     BIGINT       NOT NULL DEFAULT 0,
    deleted_at  TIMESTAMPTZ  NULL,
    CONSTRAINT pk_projects PRIMARY KEY (id)
);
CREATE INDEX idx_projects_tenant ON projects (tenant_id);
CREATE INDEX idx_projects_owner ON projects (owner_id, tenant_id);
