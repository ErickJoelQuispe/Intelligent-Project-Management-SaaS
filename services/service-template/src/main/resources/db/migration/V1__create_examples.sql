-- V1: Create examples table
-- This is the base migration for service-template.
-- The 'examples' table persists Example domain objects created via ExampleJpaEntity.

CREATE TABLE IF NOT EXISTS examples (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    tenant_id   UUID NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
