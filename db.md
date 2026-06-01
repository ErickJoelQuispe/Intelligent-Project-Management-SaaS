# Diseño de bases de datos — Intelligent Project Management SaaS

> Este documento define la estrategia de persistencia, el esquema por microservicio, índices, particionamiento, multi-tenancy y políticas operativas.
> **Audiencia:** desarrollador único que aprende y quiere mostrar criterio de DBA al diseñar.
> **Engine principal:** PostgreSQL 16+ (uno por servicio).

---

## 1. Decisión raíz: ¿una DB o varias?

**Elegido:** **PostgreSQL 16 con database-per-service** sobre el mismo cluster en dev, clusters separados en prod.

### ¿Por qué PostgreSQL en TODOS los servicios y no polyglot persistence?

Para un sistema de gestión de proyectos como este, Postgres cubre TODOS los casos:

| Necesidad | ¿Postgres lo resuelve? | Cómo |
|----------|------------------------|------|
| Datos relacionales (users, projects, tasks) | Sí, naturalmente | Schemas + FK |
| Datos semi-estructurados (settings, metadata) | Sí | JSONB + GIN indexes |
| Búsqueda full-text (en tareas/comentarios) | Sí | `tsvector` + GIN |
| Time-series (activity logs, eventos) | Sí | Partitioning por rango |
| Cache de embeddings IA | Sí | extensión `pgvector` |
| Geo (futuro: mapas de oficinas) | Sí | PostGIS |
| Cola de outbox | Sí | tabla + scheduled poller / Debezium |

**Polyglot persistence** (Mongo + Postgres + Elastic + Redis + ...) es el típico over-engineering que te hace perder semanas configurando cosas en lugar de aprender arquitectura. **Una sola tecnología**, bien aprendida y bien indexada, gana 10 a 1.

### ¿Y Redis?

**Sí, también — pero NO como base de datos primaria.** Redis cumple:
- Cache de respuestas de IA (key = hash de prompt).
- Rate limiting (sliding window counters).
- Sesiones en gateway si fueran stateful (no aplica acá: usamos JWT).
- Distributed locks ocasionales.

Redis NUNCA tiene la verdad. Si Redis muere, el sistema sigue funcionando, solo más lento o más caro.

### ¿Una instancia o varias?

| Entorno | Configuración |
|---------|---------------|
| Local (Docker Compose) | **Un cluster Postgres**, múltiples DBs (`auth_db`, `user_db`, `project_db`, `task_db`, `ai_db`, `notification_db`). Un volumen, un proceso. |
| Staging | **Un cluster** con replica de lectura. Multi-DB. |
| Producción | **Idealmente cluster por servicio crítico** (`task_db` y `ai_db` separados; el resto puede compartir). Streaming replication con un standby por cluster. |

**Crítico:** "database-per-service" no implica "cluster-per-service". Lo que importa es **aislamiento lógico** (sin cross-DB queries, sin FK cruzadas, sin locks compartidos).

### Versión de PostgreSQL

**PostgreSQL 16.x** (16.4+ al 2026). Razones:
- Mejoras enormes de paralelismo en VACUUM y consultas.
- Logical replication más maduro (útil para outbox vía Debezium).
- `JSONB` con paths optimizados.
- `SQL/JSON` standard.
- pgvector estable.

**PG 17** está disponible — usable, pero las mejoras son incrementales y muchos hosting providers todavía están en 16. Vamos por la conservadora.

---

## 2. Multi-tenancy: estrategia

### Decisión: **shared schema + `tenant_id` por fila** (Discriminator pattern)

| Estrategia | Aislamiento | Costo operativo | Cuándo elegirla |
|-----------|-------------|----------------|-----------------|
| **Database-per-tenant** | Total | Altísimo (N DBs) | Solo enterprise con compliance estricto |
| **Schema-per-tenant** | Alto | Alto (N schemas) | Pocos tenants, datos sensibles |
| **Discriminator (`tenant_id`)** | Medio (a nivel app) | Bajo | **Default razonable para SaaS** ← elegida |

### Implementación

1. Cada tabla de dominio tiene columna `tenant_id UUID NOT NULL`.
2. Índice compuesto `(tenant_id, ...)` en TODAS las queries — es la primera columna.
3. **Hibernate Filters** activan automáticamente `WHERE tenant_id = :currentTenant` en cada query.
4. El `tenant_id` se extrae del JWT (claim `tenant_id`) por un interceptor.
5. **Row-Level Security (RLS) de Postgres** como segunda barrera defensiva (defense in depth):

```sql
ALTER TABLE projects ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON projects
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
```

El servicio setea `SET LOCAL app.tenant_id = 'xxx'` al iniciar transacción. Si la app olvida filtrar, Postgres lo bloquea.

### Migración futura a schema-per-tenant

Si un cliente enterprise lo exige, la migración es: extraer todas sus filas a un schema dedicado, redirigir su connection. La app no cambia mucho porque ya usa `tenant_id` como key lógica.

---

## 3. Convenciones globales del esquema

### 3.1 Naming

- Tablas: `snake_case` plural — `users`, `task_assignments`.
- Columnas: `snake_case` — `created_at`, `assignee_id`.
- PKs: `id UUID` (default; ver más abajo).
- FKs: `<entidad>_id` — `project_id`, `assignee_id`.
- Índices: `idx_<tabla>_<columnas>` — `idx_tasks_project_status`.
- Únicos: `uq_<tabla>_<columnas>` — `uq_users_email`.
- Constraints check: `ck_<tabla>_<descripción>`.

### 3.2 Tipos por defecto

| Concepto | Tipo |
|----------|------|
| ID | `UUID` (gen_random_uuid()) |
| Timestamp | `TIMESTAMPTZ` (siempre con timezone) |
| Texto corto (< 255) | `VARCHAR(N)` con check |
| Texto largo / libre | `TEXT` |
| Enum | `VARCHAR + CHECK` o tipo enum nativo (preferimos check por flexibilidad) |
| Money | `NUMERIC(19,4)` (NUNCA `float`) |
| Cantidad pequeña | `INTEGER` |
| Cantidad grande | `BIGINT` |
| Booleano | `BOOLEAN NOT NULL DEFAULT false` |
| JSON | `JSONB` (NUNCA `JSON`) |
| Array | `<tipo>[]` solo cuando justifica un `GIN` |

### 3.3 ¿UUID o BIGSERIAL?

**Elegido: UUID v7** (time-sortable, generado en app o con `uuidv7()` extension).

| | UUID v4 | UUID v7 | BIGSERIAL |
|--|---------|---------|-----------|
| Globalmente único | ✅ | ✅ | ❌ |
| Generable en cliente | ✅ | ✅ | ❌ |
| Indexable eficiente | ❌ (random) | ✅ (sortable) | ✅ |
| Descubre cardinalidad | ❌ | ❌ | ✅ |
| Tamaño | 16 bytes | 16 bytes | 8 bytes |

UUID v7 da lo mejor de ambos: única globalmente (necesario en sistemas distribuidos donde múltiples servicios crean entidades en paralelo) y sortable cronológicamente (B-tree feliz, hot pages al final).

### 3.4 Columnas obligatorias en TODAS las tablas de dominio

```sql
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
tenant_id UUID NOT NULL,
created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
created_by UUID,                  -- user que creó
updated_by UUID,                  -- último user que modificó
version BIGINT NOT NULL DEFAULT 0,-- optimistic locking (Hibernate @Version)
deleted_at TIMESTAMPTZ            -- soft delete; NULL = vivo
```

**Soft delete vs hard delete:** soft delete por default. Hard delete solo en tablas auditables (logs, eventos, cuando legalmente se debe borrar).

### 3.5 Trigger de `updated_at`

```sql
CREATE OR REPLACE FUNCTION trg_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

Aplicado en cada tabla:

```sql
CREATE TRIGGER set_updated_at BEFORE UPDATE ON projects
FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
```

### 3.6 Outbox table (estándar en cada DB de servicio)

Cada DB que publica eventos tiene esta tabla **idéntica**:

```sql
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL DEFAULT 1,
    payload JSONB NOT NULL,
    headers JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    tenant_id UUID NOT NULL,
    trace_id UUID
);

-- Índice para el poller: encuentra rápido lo no publicado, ordenado por tiempo
CREATE INDEX idx_outbox_unpublished
  ON outbox_events (occurred_at)
  WHERE published_at IS NULL;

-- Índice por agregado para investigación
CREATE INDEX idx_outbox_aggregate
  ON outbox_events (aggregate_type, aggregate_id, occurred_at DESC);
```

**Política de retención:** filas con `published_at IS NOT NULL AND occurred_at < now() - interval '7 days'` se borran con job semanal. Si necesitás auditoría, mové a tabla de archivo antes.

### 3.7 Inbox / Processed events (estándar en cada DB consumer)

```sql
CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,        -- idempotency key
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    consumer_group VARCHAR(100) NOT NULL
);

CREATE INDEX idx_processed_events_processed_at
  ON processed_events (processed_at);
```

Limpieza: filas más viejas que el TTL de retención de Kafka (7 días por default) se pueden borrar con seguridad.

---

## 4. Esquema por microservicio

### 4.1 `auth_db` (auth-service)

> **Importante:** las credenciales **viven en Keycloak**, no acá. Esta DB solo guarda metadata local que el `auth-service` necesita y no encaja en Keycloak.

#### Tablas

```sql
-- Espejo local de cuentas (referencia al user de Keycloak)
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_user_id UUID NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    tenant_id UUID NOT NULL,
    last_login_at TIMESTAMPTZ,
    last_login_ip INET,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    mfa_enabled BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_accounts_email_tenant UNIQUE (email, tenant_id)
);

CREATE INDEX idx_accounts_tenant ON accounts (tenant_id);
CREATE INDEX idx_accounts_email ON accounts (email);

-- Dispositivos confiables (para MFA / fingerprinting)
CREATE TABLE trusted_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    device_fingerprint VARCHAR(255) NOT NULL,
    user_agent TEXT,
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    tenant_id UUID NOT NULL,

    CONSTRAINT uq_devices UNIQUE (account_id, device_fingerprint)
);

CREATE INDEX idx_devices_account ON trusted_devices (account_id);
CREATE INDEX idx_devices_expiry ON trusted_devices (expires_at);

-- Refresh token registry (revocación)
CREATE TABLE revoked_tokens (
    jti UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(50)
);

CREATE INDEX idx_revoked_tokens_expiry ON revoked_tokens (expires_at);
-- Job nocturno borra los expirados

-- Audit log de eventos de seguridad
CREATE TABLE security_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID,
    event_type VARCHAR(50) NOT NULL,  -- LOGIN_SUCCESS, LOGIN_FAILED, MFA_FAILED, etc
    ip INET,
    user_agent TEXT,
    metadata JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    tenant_id UUID NOT NULL
) PARTITION BY RANGE (occurred_at);

-- Particiones mensuales (ver sección 6)

CREATE INDEX idx_sec_events_account ON security_events (account_id, occurred_at DESC);
CREATE INDEX idx_sec_events_type ON security_events (event_type, occurred_at DESC);
```

#### Notas de diseño

- `failed_attempts` y `locked_until` permiten rate limiting de login server-side, complementando lo que haga Keycloak.
- `revoked_tokens` solo guarda hasta `expires_at`; después de eso el JWT ya no es válido por su propia expiración.
- `security_events` particionada por mes para no degradar con el tiempo.

---

### 4.2 `user_db` (user-service)

```sql
-- Perfil de usuario (datos no relacionados a auth)
CREATE TABLE user_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL UNIQUE,  -- referencia lógica a accounts.id
    display_name VARCHAR(100) NOT NULL,
    avatar_url TEXT,
    bio TEXT,
    timezone VARCHAR(50) NOT NULL DEFAULT 'UTC',
    locale VARCHAR(10) NOT NULL DEFAULT 'en-US',
    preferences JSONB NOT NULL DEFAULT '{}'::jsonb,
    tenant_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_profiles_tenant ON user_profiles (tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_profiles_account ON user_profiles (account_id);
-- Para preferencias específicas (ej: notification_email = true)
CREATE INDEX idx_profiles_preferences_gin ON user_profiles USING GIN (preferences jsonb_path_ops);

-- Equipos
CREATE TABLE teams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    owner_profile_id UUID NOT NULL REFERENCES user_profiles(id),
    settings JSONB NOT NULL DEFAULT '{}'::jsonb,
    tenant_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ,

    CONSTRAINT uq_teams_name_tenant UNIQUE (tenant_id, name)
);

CREATE INDEX idx_teams_tenant ON teams (tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_teams_owner ON teams (owner_profile_id);

-- Membresías (relación many-to-many con rol)
CREATE TABLE team_memberships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    profile_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,   -- OWNER, ADMIN, MEMBER, GUEST
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    invited_by UUID REFERENCES user_profiles(id),
    tenant_id UUID NOT NULL,

    CONSTRAINT uq_membership UNIQUE (team_id, profile_id),
    CONSTRAINT ck_role CHECK (role IN ('OWNER','ADMIN','MEMBER','GUEST'))
);

CREATE INDEX idx_memberships_profile ON team_memberships (profile_id, team_id);
CREATE INDEX idx_memberships_team_role ON team_memberships (team_id, role);

-- Outbox + Inbox estándar
-- (omitidas por brevedad, ver sección 3.6 / 3.7)
```

---

### 4.3 `project_db` (project-service)

```sql
CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, ARCHIVED, COMPLETED
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE', -- PRIVATE, TEAM, PUBLIC
    owner_profile_id UUID NOT NULL,
    settings JSONB NOT NULL DEFAULT '{}'::jsonb,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    tenant_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ,

    CONSTRAINT ck_status CHECK (status IN ('ACTIVE','ARCHIVED','COMPLETED')),
    CONSTRAINT ck_visibility CHECK (visibility IN ('PRIVATE','TEAM','PUBLIC')),
    CONSTRAINT ck_dates CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at >= starts_at)
);

-- Patrón crítico: tenant_id PRIMERO en índices compuestos
CREATE INDEX idx_projects_tenant_status
  ON projects (tenant_id, status)
  WHERE deleted_at IS NULL;

CREATE INDEX idx_projects_owner
  ON projects (owner_profile_id)
  WHERE deleted_at IS NULL;

-- Búsqueda por nombre (case-insensitive)
CREATE INDEX idx_projects_name_lower
  ON projects (tenant_id, lower(name))
  WHERE deleted_at IS NULL;

-- Settings/metadata queries
CREATE INDEX idx_projects_metadata_gin
  ON projects USING GIN (metadata jsonb_path_ops);

-- Relación project ↔ team (un project puede tener N teams asignados)
CREATE TABLE project_teams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    team_id UUID NOT NULL,         -- referencia lógica a user_db.teams.id
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    assigned_by UUID,
    tenant_id UUID NOT NULL,

    CONSTRAINT uq_project_team UNIQUE (project_id, team_id)
);

CREATE INDEX idx_project_teams_team ON project_teams (team_id, project_id);

-- Project members (override de team — usuarios específicos con acceso)
CREATE TABLE project_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    profile_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    added_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    tenant_id UUID NOT NULL,

    CONSTRAINT uq_project_member UNIQUE (project_id, profile_id),
    CONSTRAINT ck_pm_role CHECK (role IN ('OWNER','MANAGER','CONTRIBUTOR','VIEWER'))
);

CREATE INDEX idx_project_members_profile ON project_members (profile_id, project_id);
```

---

### 4.4 `task_db` (task-service) — el motor del sistema

Este es el servicio con **más volumen de datos y más patrones de query distintos**. Acá aplicamos todo lo aprendido.

```sql
CREATE TABLE tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,           -- referencia lógica a project_db
    parent_task_id UUID REFERENCES tasks(id) ON DELETE CASCADE,  -- subtareas
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'TODO',
    priority SMALLINT NOT NULL DEFAULT 3,  -- 1 (highest) a 5 (lowest)
    assignee_profile_id UUID,
    reporter_profile_id UUID NOT NULL,
    due_date TIMESTAMPTZ,
    estimated_hours NUMERIC(6,2),
    actual_hours NUMERIC(6,2),
    labels TEXT[] NOT NULL DEFAULT '{}',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    -- Campo computado para FTS
    search_vector tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(title,'')), 'A') ||
        setweight(to_tsvector('simple', coalesce(description,'')), 'B')
    ) STORED,
    tenant_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ,

    CONSTRAINT ck_task_status CHECK (status IN ('BACKLOG','TODO','IN_PROGRESS','BLOCKED','IN_REVIEW','DONE','CANCELLED')),
    CONSTRAINT ck_priority CHECK (priority BETWEEN 1 AND 5),
    CONSTRAINT ck_hours CHECK (estimated_hours IS NULL OR estimated_hours >= 0)
);

-- Índices: pensados desde los queries reales del producto

-- Q: "tareas del proyecto X en estado Y, ordenadas por prioridad y fecha"
-- Vista Kanban (la más frecuente)
CREATE INDEX idx_tasks_project_status_priority
  ON tasks (tenant_id, project_id, status, priority, due_date)
  WHERE deleted_at IS NULL;

-- Q: "mis tareas asignadas, ordenadas por due_date"
CREATE INDEX idx_tasks_assignee_due
  ON tasks (tenant_id, assignee_profile_id, status, due_date)
  WHERE deleted_at IS NULL AND assignee_profile_id IS NOT NULL;

-- Q: "subtareas de X"
CREATE INDEX idx_tasks_parent
  ON tasks (parent_task_id)
  WHERE parent_task_id IS NOT NULL;

-- Q: "buscar tareas que contengan palabras"
CREATE INDEX idx_tasks_search
  ON tasks USING GIN (search_vector);

-- Q: "tareas con label X"
CREATE INDEX idx_tasks_labels
  ON tasks USING GIN (labels);

-- Q: "tareas con cierto metadata"
CREATE INDEX idx_tasks_metadata
  ON tasks USING GIN (metadata jsonb_path_ops);

-- Q: "tareas vencidas (due_date < now y no done)" — partial index muy chico
CREATE INDEX idx_tasks_overdue
  ON tasks (tenant_id, due_date)
  WHERE deleted_at IS NULL
    AND due_date IS NOT NULL
    AND status NOT IN ('DONE','CANCELLED');

-- Comentarios de tareas
CREATE TABLE task_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    author_profile_id UUID NOT NULL,
    content TEXT NOT NULL,
    mentions UUID[] NOT NULL DEFAULT '{}', -- profile_ids mencionados con @
    edited_at TIMESTAMPTZ,
    tenant_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_comments_task ON task_comments (task_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_comments_mentions ON task_comments USING GIN (mentions);

-- Adjuntos (metadata; archivos en S3-like)
CREATE TABLE task_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_url TEXT NOT NULL,
    uploaded_by UUID NOT NULL,
    tenant_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_attachments_task ON task_attachments (task_id);

-- Activity log (PARTICIONADA — ver sección 6)
CREATE TABLE task_activity_log (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL,
    actor_profile_id UUID NOT NULL,
    activity_type VARCHAR(50) NOT NULL,  -- CREATED, STATUS_CHANGED, ASSIGNED, etc
    old_value JSONB,
    new_value JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    tenant_id UUID NOT NULL,
    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

-- Particiones mensuales (ver sección 6)
CREATE INDEX idx_activity_task_time ON task_activity_log (task_id, occurred_at DESC);
CREATE INDEX idx_activity_actor ON task_activity_log (actor_profile_id, occurred_at DESC);
```

#### Vista materializada: tablero Kanban

Para evitar recalcular en cada request:

```sql
CREATE MATERIALIZED VIEW project_kanban_summary AS
SELECT
    project_id,
    tenant_id,
    status,
    COUNT(*) AS task_count,
    COUNT(*) FILTER (WHERE due_date < now() AND status NOT IN ('DONE','CANCELLED')) AS overdue_count,
    AVG(priority)::numeric(3,2) AS avg_priority,
    MAX(updated_at) AS last_activity
FROM tasks
WHERE deleted_at IS NULL
GROUP BY project_id, tenant_id, status;

CREATE UNIQUE INDEX idx_kanban_summary_unique
  ON project_kanban_summary (project_id, status);
```

Refresh: `REFRESH MATERIALIZED VIEW CONCURRENTLY project_kanban_summary` cada N minutos o vía trigger en cambios significativos.

---

### 4.5 `ai_db` (ai-service) — con pgvector

```sql
CREATE EXTENSION IF NOT EXISTS vector;

-- Log de cada request a IA (para tracking de costo y debugging)
CREATE TABLE ai_request_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_type VARCHAR(50) NOT NULL,  -- TASK_GENERATION, SUMMARY, CHAT, etc
    provider VARCHAR(20) NOT NULL,      -- openai, anthropic, ollama
    model VARCHAR(100) NOT NULL,
    prompt_hash VARCHAR(64) NOT NULL,   -- SHA-256 del prompt para cache lookup
    prompt_tokens INTEGER NOT NULL DEFAULT 0,
    completion_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens INTEGER GENERATED ALWAYS AS (prompt_tokens + completion_tokens) STORED,
    cost_usd NUMERIC(10,6),
    duration_ms INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,        -- SUCCESS, FAILED, CACHED
    error_message TEXT,
    requested_by_profile_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now()
) PARTITION BY RANGE (requested_at);  -- particionada mensual

CREATE INDEX idx_ai_log_user_time ON ai_request_log (requested_by_profile_id, requested_at DESC);
CREATE INDEX idx_ai_log_tenant_cost ON ai_request_log (tenant_id, requested_at, cost_usd);
CREATE INDEX idx_ai_log_prompt_hash ON ai_request_log (prompt_hash, status);

-- Cache persistente de respuestas (Redis es el L1; esto es L2 para cold reload)
CREATE TABLE ai_response_cache (
    prompt_hash VARCHAR(64) PRIMARY KEY,
    provider VARCHAR(20) NOT NULL,
    model VARCHAR(100) NOT NULL,
    response_payload JSONB NOT NULL,
    hits INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_hit_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_cache_expiry ON ai_response_cache (expires_at);

-- Embeddings para RAG (fase avanzada)
CREATE TABLE document_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_type VARCHAR(50) NOT NULL,   -- TASK, COMMENT, PROJECT_DESCRIPTION
    source_id UUID NOT NULL,
    project_id UUID NOT NULL,
    chunk_index INTEGER NOT NULL DEFAULT 0,
    chunk_text TEXT NOT NULL,
    embedding vector(1536) NOT NULL,    -- ajustar a la dim del modelo elegido
    embedding_model VARCHAR(100) NOT NULL,
    tenant_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_embedding UNIQUE (source_type, source_id, chunk_index, embedding_model)
);

CREATE INDEX idx_embeddings_source ON document_embeddings (source_type, source_id);
CREATE INDEX idx_embeddings_project ON document_embeddings (tenant_id, project_id);

-- Índice IVFFlat para búsqueda vectorial aproximada
-- (HNSW es mejor pero requiere más config; IVFFlat es buen default)
CREATE INDEX idx_embeddings_vector
  ON document_embeddings
  USING ivfflat (embedding vector_cosine_ops)
  WITH (lists = 100);

-- Cuotas de uso por usuario/tenant
CREATE TABLE ai_quotas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope_type VARCHAR(20) NOT NULL,    -- USER, TENANT
    scope_id UUID NOT NULL,
    period VARCHAR(20) NOT NULL,        -- DAILY, MONTHLY
    request_limit INTEGER NOT NULL,
    token_limit BIGINT,
    cost_limit_usd NUMERIC(10,2),
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    requests_used INTEGER NOT NULL DEFAULT 0,
    tokens_used BIGINT NOT NULL DEFAULT 0,
    cost_used_usd NUMERIC(10,6) NOT NULL DEFAULT 0,
    tenant_id UUID NOT NULL,

    CONSTRAINT uq_quota UNIQUE (scope_type, scope_id, period, period_start)
);

CREATE INDEX idx_quotas_scope ON ai_quotas (scope_type, scope_id, period_end);
```

---

### 4.6 `notification_db` (notification-service)

```sql
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_profile_id UUID NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    channel VARCHAR(20) NOT NULL,      -- IN_APP, EMAIL, PUSH
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, SENT, READ, FAILED
    read_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    failure_reason TEXT,
    related_entity_type VARCHAR(50),
    related_entity_id UUID,
    tenant_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_notif_channel CHECK (channel IN ('IN_APP','EMAIL','PUSH','SLACK')),
    CONSTRAINT ck_notif_status CHECK (status IN ('PENDING','SENT','READ','FAILED'))
) PARTITION BY RANGE (created_at);

-- Particiones mensuales

-- "Mis notificaciones no leídas"
CREATE INDEX idx_notif_recipient_unread
  ON notifications (recipient_profile_id, created_at DESC)
  WHERE status IN ('PENDING','SENT') AND read_at IS NULL;

-- "Pendientes de enviar" (job dispatcher las toma)
CREATE INDEX idx_notif_pending
  ON notifications (channel, created_at)
  WHERE status = 'PENDING';

-- Suscripciones del usuario (qué tipo + canal quiere recibir)
CREATE TABLE notification_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id UUID NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    tenant_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_subscription UNIQUE (profile_id, notification_type, channel)
);

CREATE INDEX idx_subscriptions_profile ON notification_subscriptions (profile_id);

-- Templates de email/push versionados
CREATE TABLE notification_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_key VARCHAR(100) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    locale VARCHAR(10) NOT NULL DEFAULT 'en-US',
    subject VARCHAR(255),
    body TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_template UNIQUE (template_key, channel, locale, version)
);

CREATE INDEX idx_templates_lookup ON notification_templates (template_key, channel, locale, active);
```

---

## 5. Estrategia de indexado: principios aplicados

### Reglas que TODOS los índices del proyecto siguen

1. **`tenant_id` SIEMPRE primero en índices compuestos.** Multi-tenancy = el filtro más selectivo.
2. **Igualdad antes que rango.** En `idx_tasks_project_status_priority`: project_id (=) → status (=) → priority (=) → due_date (range).
3. **Partial indexes para soft delete.** Casi todos los índices llevan `WHERE deleted_at IS NULL`. El planner los elige automáticamente cuando la query incluye ese filtro.
4. **GIN para JSONB y arrays.** `jsonb_path_ops` cuando solo necesitamos `@>` (más chico, más rápido).
5. **Expression indexes para búsquedas case-insensitive** — `lower(name)` en vez de `ILIKE`.
6. **No indexar lo que no se consulta.** Cada índice cuesta en escritura. Antes de crear, verificar `pg_stat_user_indexes`.
7. **`CREATE INDEX CONCURRENTLY` en producción**, siempre. Sin lock de tabla.
8. **Covering indexes (`INCLUDE`)** cuando la query devuelve siempre las mismas columnas — habilita index-only scans.

### Ejemplo real de análisis

Query típica del Kanban:
```sql
SELECT * FROM tasks
WHERE tenant_id = $1
  AND project_id = $2
  AND status = $3
  AND deleted_at IS NULL
ORDER BY priority ASC, due_date ASC NULLS LAST
LIMIT 50;
```

Índice óptimo:
```sql
CREATE INDEX idx_tasks_project_status_priority
  ON tasks (tenant_id, project_id, status, priority, due_date)
  WHERE deleted_at IS NULL;
```

Esto permite **index-only scan parcial**: filtra por las primeras 3 columnas, ordena por las últimas 2 sin sort adicional, y el `WHERE deleted_at IS NULL` ya está incorporado al índice.

---

## 6. Particionamiento

### Tablas que se particionan (alto volumen + patrón temporal)

| Tabla | Strategy | Granularidad | Justificación |
|-------|----------|--------------|---------------|
| `task_activity_log` | Range by `occurred_at` | Mensual | Crece sin parar; queries siempre incluyen rango temporal |
| `security_events` | Range by `occurred_at` | Mensual | Audit, retención 1 año, después archive |
| `notifications` | Range by `created_at` | Mensual | Volumen alto, datos viejos rara vez consultados |
| `ai_request_log` | Range by `requested_at` | Mensual | Tracking de costo histórico |

### Plantilla de creación + automatización

```sql
-- Tabla padre
CREATE TABLE task_activity_log (
    -- columnas...
    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

-- Función para crear partición mensual
CREATE OR REPLACE FUNCTION create_monthly_partition(
    p_table_name TEXT,
    p_start_date DATE
) RETURNS VOID AS $$
DECLARE
    v_partition_name TEXT;
    v_end_date DATE;
BEGIN
    v_partition_name := p_table_name || '_' || to_char(p_start_date, 'YYYY_MM');
    v_end_date := p_start_date + INTERVAL '1 month';

    EXECUTE format(
      'CREATE TABLE IF NOT EXISTS %I PARTITION OF %I
       FOR VALUES FROM (%L) TO (%L)',
      v_partition_name, p_table_name, p_start_date, v_end_date
    );
END;
$$ LANGUAGE plpgsql;

-- Crear próximas 3 particiones (job mensual)
SELECT create_monthly_partition('task_activity_log', date_trunc('month', now())::date);
SELECT create_monthly_partition('task_activity_log', date_trunc('month', now() + interval '1 month')::date);
SELECT create_monthly_partition('task_activity_log', date_trunc('month', now() + interval '2 months')::date);
```

Job de mantenimiento (Spring Scheduled o `pg_cron`):
- 1° de cada mes: crear partición del mes +2.
- Mover particiones >12 meses a tablespace de archivo o exportar y dropear.

### Lo que NO particionamos

- `users`, `projects`, `teams`: no crecen exponencialmente, joins frecuentes con condiciones que no son temporales.
- Tablas con < 10M filas proyectadas — partition pruning no se justifica.

---

## 7. MVCC, VACUUM y mantenimiento

### Tablas hot que requieren autovacuum agresivo

`tasks` y `outbox_events` reciben muchísimos UPDATEs/DELETEs. Configuración por tabla:

```sql
ALTER TABLE tasks SET (
    autovacuum_vacuum_scale_factor = 0.05,    -- vacuum cuando 5% son dead (default 20%)
    autovacuum_analyze_scale_factor = 0.02,
    autovacuum_vacuum_cost_limit = 1000,
    fillfactor = 90                            -- deja 10% libre para HOT updates
);

ALTER TABLE outbox_events SET (
    autovacuum_vacuum_scale_factor = 0.02,
    fillfactor = 80
);
```

### Monitoreo obligatorio (queries para dashboard Grafana)

```sql
-- Bloat de tablas críticas
SELECT schemaname, tablename,
       n_live_tup, n_dead_tup,
       round(n_dead_tup * 100.0 / NULLIF(n_live_tup + n_dead_tup, 0), 2) AS dead_pct,
       last_autovacuum
FROM pg_stat_user_tables
WHERE n_dead_tup > 1000
ORDER BY dead_pct DESC;

-- Cache hit ratio (debe ser > 95% en steady state)
SELECT sum(blks_hit) * 100.0 / nullif(sum(blks_hit) + sum(blks_read), 0) AS cache_hit_pct
FROM pg_stat_database WHERE datname = current_database();

-- Long-running transactions (peligrosos: bloquean autovacuum)
SELECT pid, age(clock_timestamp(), xact_start) AS xact_age, state, query
FROM pg_stat_activity
WHERE xact_start IS NOT NULL
ORDER BY xact_start;

-- Índices no usados (candidatos a borrar)
SELECT schemaname, relname AS table, indexrelname AS index,
       pg_size_pretty(pg_relation_size(indexrelid)) AS size,
       idx_scan
FROM pg_stat_user_indexes
WHERE idx_scan = 0
  AND indexrelname NOT LIKE 'pk_%'
ORDER BY pg_relation_size(indexrelid) DESC;
```

---

## 8. Configuración de Postgres por entorno

### Local (Docker, 4GB asignados)

```conf
shared_buffers = 1GB
effective_cache_size = 3GB
work_mem = 16MB
maintenance_work_mem = 256MB
max_connections = 100
random_page_cost = 1.1                 # SSD asumido
effective_io_concurrency = 200
checkpoint_completion_target = 0.9
wal_buffers = 16MB
default_statistics_target = 100
max_wal_size = 2GB
min_wal_size = 80MB
```

### Producción (16GB RAM, dedicado)

```conf
shared_buffers = 4GB                   # 25% RAM
effective_cache_size = 12GB            # 75% RAM
work_mem = 32MB                        # Cuidado: se multiplica por conexión x sort
maintenance_work_mem = 1GB
max_connections = 200                  # Bajo: usar pgBouncer para más
random_page_cost = 1.1
effective_io_concurrency = 200
checkpoint_completion_target = 0.9
wal_buffers = 64MB
default_statistics_target = 200        # Estadísticas más detalladas
max_wal_size = 8GB
min_wal_size = 2GB

# Replicación
wal_level = replica
max_wal_senders = 10
max_replication_slots = 10
hot_standby = on

# Logging
log_min_duration_statement = 500       # log queries > 500ms
log_checkpoints = on
log_connections = on
log_lock_waits = on
log_temp_files = 0
log_autovacuum_min_duration = 0

# Extensions a precargar
shared_preload_libraries = 'pg_stat_statements,auto_explain'
auto_explain.log_min_duration = 1s
auto_explain.log_analyze = on
```

### pgBouncer en producción

```ini
[databases]
auth_db = host=postgres-auth port=5432 dbname=auth_db
user_db = host=postgres-user port=5432 dbname=user_db
# ...

[pgbouncer]
pool_mode = transaction
default_pool_size = 25
max_client_conn = 1000
reserve_pool_size = 5
server_idle_timeout = 600
```

**Modo transaction**: aprovecha al máximo conexiones reales. Atención: incompatible con prepared statements del lado del servidor (Hibernate puede manejarlo con configuración).

---

## 9. Replicación y alta disponibilidad

### Plan progresivo

1. **Fase 0-9 (dev/staging):** sin réplica. Backup diario con `pg_dump`.
2. **Fase 10+ (cuando se quiera demo en producción):** streaming replication asíncrona con un standby por cluster crítico.
3. **Solo si el proyecto se vuelve real:** Patroni + 3 nodos + sync replication para HA con failover automático.

### Configuración inicial de streaming replication

Primary `postgresql.conf`:
```conf
wal_level = replica
max_wal_senders = 5
max_replication_slots = 5
```

Crear usuario:
```sql
CREATE ROLE replicator WITH REPLICATION LOGIN PASSWORD 'xxx';
```

Standby setup vía `pg_basebackup`:
```bash
pg_basebackup -h primary -D /var/lib/postgresql/data -U replicator -P -R -X stream -C -S standby1
```

Standby ya queda con `standby.signal` y `primary_conninfo` configurados.

---

## 10. Backup y recovery

### Estrategia 3-2-1 simplificada

| Tipo | Frecuencia | Retención | Storage |
|------|-----------|-----------|---------|
| Logical (`pg_dump`) | Diaria 3am | 7 días | Local + S3 (encrypted) |
| Physical (`pg_basebackup`) | Semanal domingo | 4 semanas | S3 |
| WAL archiving | Continuo | 7 días | S3 |

### PITR (Point-in-Time Recovery) habilitado

```conf
wal_level = replica
archive_mode = on
archive_command = 'aws s3 cp %p s3://my-wal-archive/%f'
restore_command = 'aws s3 cp s3://my-wal-archive/%f %p'
```

### Test de restore obligatorio

**Una vez por mes** se restaura el último backup en una máquina aislada y se verifica integridad. Backup que no se prueba **NO existe**.

---

## 11. Migraciones con Flyway

### Convenciones

- Ubicación: `src/main/resources/db/migration/`.
- Naming: `V{version}__{description}.sql` — ejemplo `V001__create_users.sql`.
- Versionado independiente por servicio (cada servicio tiene su carpeta).
- **Migraciones inmutables**: una vez en `main`, no se editan. Se crea `V002__fix_xxx.sql`.
- Repeatable migrations: `R__update_views.sql` para vistas y funciones que se sobrescriben.

### Reglas de seguridad

1. **`CREATE INDEX CONCURRENTLY`** siempre en producción. Flyway soporta esto con `-- skip transaction` annotation.
2. **NO bloquear tablas más de unos segundos.** Para columnas NOT NULL, dividir en pasos:
   - Migración 1: agregar columna nullable.
   - App: empezar a escribir el campo.
   - Migración 2: backfill en batches (job aplicación, no migración).
   - Migración 3: `SET NOT NULL` (después de verificar 0 nulls).
3. **Cada migración con rollback documentado** en comentario (Flyway Community no tiene rollback automático; está bien — forzás pensar antes de ejecutar).

### Ejemplo de migración bien hecha

```sql
-- V015__add_task_archived_flag.sql
-- Rollback manual: ALTER TABLE tasks DROP COLUMN archived;

ALTER TABLE tasks ADD COLUMN archived BOOLEAN NOT NULL DEFAULT false;

-- Index parcial para queries de tareas activas (la inmensa mayoría)
CREATE INDEX CONCURRENTLY idx_tasks_active
  ON tasks (tenant_id, project_id)
  WHERE archived = false AND deleted_at IS NULL;
```

---

## 12. Testing de la capa de datos

### Testcontainers obligatorio

Cada servicio tiene tests de integración que levantan PostgreSQL real (no H2):

```java
@Testcontainers
@SpringBootTest
class TaskRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("task_test")
        .withReuse(true);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // ... tests reales contra Postgres real
}
```

**Por qué NO H2/embedded:**
- H2 NO soporta JSONB, arrays, partial indexes, FTS, pgvector.
- Bugs aparecen en producción que jamás aparecieron en tests.
- Testcontainers con `withReuse(true)` es rapidísimo (1 vez levanta, reusa).

### Validar migraciones antes de merge

CI corre Flyway contra Postgres limpio y rechaza el PR si:
- Migración rompe (sintaxis).
- Migración existente fue modificada (hash changed).
- ArchUnit detecta uso de `@Query` con SQL nativo no-parametrizado.

---

## 13. Decisiones con tradeoffs

### 13.1 ¿Por qué NO usar tipos `ENUM` nativos de Postgres?

**Decidido:** `VARCHAR + CHECK` en vez de `CREATE TYPE x AS ENUM`.
**Tradeoff:** ENUMs nativos son más eficientes (1 byte) y validados a nivel DB.
**Razón:** agregar valores a un ENUM en producción requiere `ALTER TYPE`, que tiene limitaciones. `VARCHAR + CHECK` se modifica con un `DROP/ADD CONSTRAINT` simple. Para un sistema en evolución, flexibilidad gana.

### 13.2 ¿Por qué soft delete en vez de hard delete?

**Decidido:** soft delete por default.
**Tradeoff:** crece la tabla, queries deben filtrar por `deleted_at IS NULL`.
**Razón:** facilita "undelete", auditoría, y resolver inconsistencias eventuales causadas por eventos perdidos. El costo se mitiga con partial indexes.

### 13.3 ¿Por qué FK lógicas (UUID sin REFERENCES) entre servicios?

**Decidido:** referencias a entidades de OTROS servicios son UUIDs sin FK.
**Tradeoff:** podés tener IDs huérfanos si un evento se pierde.
**Razón:** FK entre DBs distintas es imposible. Eventual consistency lo resuelve: eventos de borrado disparan limpieza en consumers.

### 13.4 ¿pgvector vs Pinecone/Weaviate?

**Decidido:** pgvector dentro de `ai_db`.
**Tradeoff:** menos features que un vector DB dedicado.
**Razón:** un servicio menos para operar; suficiente para MVP de RAG. Migrable si crece la necesidad.

### 13.5 ¿Materialized view vs read model en CQRS puro?

**Decidido:** materialized views para Kanban summary; CQRS completo solo si surge necesidad.
**Tradeoff:** vistas materializadas no son real-time; refresh tiene costo.
**Razón:** introducir un read model separado alimentado por eventos es mucha complejidad por adelantado. Vistas materializadas son el 80% del beneficio con el 20% del costo.

---

## 14. Checklist por servicio antes de Production

Antes de marcar la DB de un servicio como "lista para producción":

- [ ] Schema completo con todas las constraints (PK, FK, UNIQUE, CHECK, NOT NULL).
- [ ] Todos los índices justificados con su query objetivo documentado.
- [ ] `tenant_id` presente en TODAS las tablas de dominio.
- [ ] RLS habilitado en TODAS las tablas con `tenant_id`.
- [ ] Outbox table presente (si publica eventos).
- [ ] `processed_events` presente (si consume eventos).
- [ ] Soft delete + trigger `updated_at` consistentes.
- [ ] Tablas de alto volumen particionadas.
- [ ] Migraciones Flyway organizadas y testeadas.
- [ ] Tests con Testcontainers cubriendo repositorios.
- [ ] `EXPLAIN ANALYZE` documentado para las 5 queries más críticas.
- [ ] Configuración de autovacuum tuneada para tablas hot.
- [ ] Monitoring queries integradas a Grafana.
- [ ] Backup script + restore test ejecutado al menos 1 vez.

---

## 15. Cosas que dejamos AFUERA (consciente)

- **Replicación lógica entre servicios** — tentador para "compartir" datos, pero rompe el principio de bounded context. Los eventos son la única fuente de propagación.
- **Stored procedures con lógica de negocio** — la lógica vive en el servicio. La DB es persistencia, no aplicación.
- **Sharding horizontal** — innecesario hasta volúmenes que un proyecto educativo no va a alcanzar.
- **Multi-master** — complejidad enorme; standby asíncrono es más que suficiente.
- **Postgres como cola de mensajes** (estilo `LISTEN/NOTIFY`) — Kafka es la cola; outbox poller usa polling estándar.

---

## 16. Próximos pasos

1. Revisar este documento contra `arquitectura.md` y verificar que **no haya contradicciones**.
2. Cuando arranque la **Fase 0**, lo primero del DB será:
   - Script `init-databases.sh` que crea las 6 DBs en Docker.
   - Plantilla de `flyway` por servicio con `V001__init_outbox.sql` y `V001__init_processed_events.sql` ya listos.
3. Cuando empiece cada servicio en su fase, empezamos con su `V002__init_<service>.sql` definiendo su schema.
4. Documentar EXPLAIN ANALYZE de cada query crítica antes de marcar el servicio como "listo".

---

> *Una base de datos bien diseñada NO se ve. Solo se nota cuando está mal hecha.*
