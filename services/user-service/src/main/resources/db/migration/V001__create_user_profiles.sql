-- User profiles table
CREATE TABLE user_profiles (
    id          UUID         NOT NULL,
    tenant_id   UUID         NOT NULL,
    email       VARCHAR(255) NOT NULL,
    first_name  VARCHAR(100) NOT NULL,
    last_name   VARCHAR(100) NOT NULL,
    bio         TEXT         NULL,
    avatar_url  VARCHAR(500) NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(255) NOT NULL DEFAULT 'system',
    updated_by  VARCHAR(255) NOT NULL DEFAULT 'system',
    version     BIGINT       NOT NULL DEFAULT 0,
    deleted_at  TIMESTAMPTZ  NULL,
    CONSTRAINT pk_user_profiles PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uix_user_profiles_tenant_email
    ON user_profiles (tenant_id, email) WHERE deleted_at IS NULL;
CREATE INDEX idx_user_profiles_tenant ON user_profiles (tenant_id);
