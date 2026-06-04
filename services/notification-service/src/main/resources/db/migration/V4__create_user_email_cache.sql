-- V4: Create user_email_cache table for email resolution without sync coupling

CREATE TABLE user_email_cache (
    user_id    UUID         NOT NULL,
    tenant_id  UUID         NOT NULL,
    email      VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_email_cache PRIMARY KEY (user_id)
);
