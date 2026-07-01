-- V008: Create invitations table for team invitation lifecycle
--
-- An invitation is created by an admin, carries a SHA-256 hash of a
-- one-time token, and expires 72 hours after creation.
-- Only one active (unused, non-expired) invitation is allowed
-- per email per tenant (enforced by the partial unique index).

CREATE TABLE invitations (
    id            UUID                     NOT NULL,
    team_id       UUID                     NOT NULL,
    tenant_id     UUID                     NOT NULL,
    email         VARCHAR(255)             NOT NULL,
    token_hash    VARCHAR(64)              NOT NULL,
    role          VARCHAR(50)              NOT NULL DEFAULT 'VIEWER',
    expires_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at       TIMESTAMP WITH TIME ZONE,
    created_by    VARCHAR(255)             NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version       BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_invitations PRIMARY KEY (id)
);

-- Fast token lookup: token_hash is the only lookup key for validation
CREATE UNIQUE INDEX idx_invitations_token_hash ON invitations(token_hash);

-- Composite index to support existsActiveInvitation() queries
CREATE INDEX idx_invitations_tenant_email ON invitations(tenant_id, email);

-- Enforce one active invitation per email per tenant.
-- An invitation is "active" when it has not been used (used_at IS NULL).
-- Expired invitations (expires_at in the past) are not covered by this index
-- so a new invitation can be issued after expiry.
CREATE UNIQUE INDEX idx_invitations_active_per_tenant_email
    ON invitations(tenant_id, email)
    WHERE used_at IS NULL;
