CREATE TABLE security_events (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL,
    account_id  UUID        NOT NULL,
    event_type  VARCHAR(50) NOT NULL,
    ip_address  VARCHAR(45) NULL,
    user_agent  TEXT        NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_security_events PRIMARY KEY (id)
);

CREATE INDEX idx_security_events_account ON security_events (account_id);
CREATE INDEX idx_security_events_tenant  ON security_events (tenant_id);
