# config-repo

Centralized configuration for the EPM SaaS monorepo, consumed by Spring Cloud Config Server.

## Structure

| File | Consumed by |
|------|-------------|
| `application.yml` | All services (shared defaults) |
| `api-gateway.yml` | api-gateway service |
| `auth-service.yml` | auth-service |
| `config-service.yml` | config-service |
| `discovery-service.yml` | discovery-service |
| `project-service.yml` | project-service |
| `task-service.yml` | task-service |
| `user-service.yml` | user-service |
| `notification-service.yml` | notification-service |

---

## API Gateway — Route Order Convention

Spring Cloud Gateway evaluates routes in **ascending order value**: the route with the **lowest** (most negative) `order` value is matched **first**.

### The Rule

> Routes with MORE SPECIFIC path patterns MUST have a LOWER (more negative) `order` than routes with BROADER patterns that cover the same path prefix.

### Why It Matters

Consider these two routes:

| Route | Pattern | Order |
|-------|---------|-------|
| `project-service` | `/api/v1/projects/**` | `0` |
| `task-service` | `/api/v1/projects/*/tasks/**` | `-1` |

Without the explicit `order: -1` on `task-service`, a request to `/api/v1/projects/abc/tasks` would be routed to `project-service` — because `/api/v1/projects/**` also matches that URL and was registered first.

### Order Assignments (current)

| Order | Service | Pattern | Reason |
|-------|---------|---------|--------|
| `-1` | task-service | `/api/v1/tasks/**`, `/api/v1/projects/*/tasks/**` | Sub-path of `/projects/**` — must precede project-service |
| `0` (default) | project-service | `/api/v1/projects/**` | Broad pattern |
| `0` (default) | notification-service | `/api/v1/notifications/**` | Independent path, no conflict |
| `0` (default) | user-service | `/api/v1/users/**`, `/api/v1/teams/**` | Independent paths |
| `0` (default) | auth-service | `/api/v1/auth/**` | Independent path |

### Adding a New Service

1. **Is the new path a sub-path of an existing route?**
   - Yes → assign `order` lower than the broad route's order (e.g., `-2` if the broad route is `-1`).
   - No → use `order: 0` (default; omit the `order:` field).

2. **Document the reasoning** with an inline comment on the `order:` line in `api-gateway.yml`.

3. **Test**: start the gateway locally and verify that requests to overlapping paths reach the correct service.
