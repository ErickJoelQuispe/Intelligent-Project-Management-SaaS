CREATE TABLE accounts (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        UUID        NOT NULL,
    keycloak_user_id UUID        NULL,
    email            VARCHAR(255) NOT NULL,
    status           VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE','LOCKED','SUSPENDED')),
    failed_attempts  INT          NOT NULL DEFAULT 0,
    last_login_at    TIMESTAMPTZ NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       VARCHAR(255) NOT NULL DEFAULT 'system',
    updated_by       VARCHAR(255) NOT NULL DEFAULT 'system',
    version          BIGINT       NOT NULL DEFAULT 0,
    deleted_at       TIMESTAMPTZ NULL,
    CONSTRAINT pk_accounts PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uix_accounts_tenant_email
    ON accounts (tenant_id, email)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_accounts_tenant_id ON accounts (tenant_id);
