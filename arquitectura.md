# Arquitectura — Intelligent Project Management SaaS

> Documento maestro de arquitectura. Esta es la fuente de verdad para todas las decisiones técnicas, estructurales y de proceso del proyecto.
> **Audiencia:** desarrollador único (vos) que está aprendiendo arquitectura distribuida con foco en presentar el proyecto a nivel profesional.

---

## 1. Resumen ejecutivo

Plataforma SaaS multi-tenant de gestión de proyectos con asistencia de IA, construida como un sistema **distribuido orientado a eventos** sobre microservicios Java. El objetivo es doble:

1. **Producto:** una herramienta tipo "Jira/Linear ligero" donde equipos pequeños y medianos crean proyectos, gestionan tareas y aprovechan IA para generación de tareas, resúmenes y sugerencias.
2. **Aprendizaje:** demostrar dominio sobre arquitectura hexagonal, DDD, comunicación síncrona/asíncrona, seguridad robusta, testing serio, observabilidad y CI/CD.

Las decisiones priorizan **claridad arquitectónica y presentabilidad** por sobre velocidad de entrega. No hay deadlines: hay fases con responsabilidades cerradas.

---

## 2. Stack tecnológico (versiones LTS al 2026)

### 2.1 Núcleo de aplicación

| Componente | Tecnología | Versión | Razón |
|-----------|-----------|---------|-------|
| Lenguaje | Java | **21 LTS** | Virtual threads, pattern matching, records, sealed classes |
| Framework | Spring Boot | **3.5.x** | Última estable, requiere Java 17+, soporte LTS hasta 2026+ |
| Microservicios | Spring Cloud | **2025.0.x (Northfields)** | Compatible con Spring Boot 3.5 |
| API Gateway | Spring Cloud Gateway | **4.3.x** | Reactor-based, filtros nativos |
| Service Discovery | Spring Cloud Netflix Eureka | **4.3.x** | Simple, ideal para aprender; alternativa Consul si querés ir más serio |
| Config Server | Spring Cloud Config | **4.3.x** | Configuración centralizada con backend Git |
| Build | Maven | **3.9.x** | Estándar Java; Gradle es alternativa válida |

### 2.2 Datos y persistencia

| Componente | Tecnología | Versión | Razón |
|-----------|-----------|---------|-------|
| RDBMS | PostgreSQL | **16.x** (16.4+) | JSONB, particionado, replicación lógica, FTS |
| ORM | Spring Data JPA + Hibernate | **6.5.x** | Productividad sin perder control |
| Migraciones | Flyway | **10.x** | Versionado SQL puro, integrado con Spring Boot |
| Cache | Redis | **7.4.x** | Sesiones, response cache de IA, rate limiting |
| Search (futuro) | OpenSearch | 2.x (opcional) | Solo si FTS de Postgres no alcanza |

### 2.3 Mensajería y eventos

| Componente | Tecnología | Versión | Razón |
|-----------|-----------|---------|-------|
| Event broker | Apache Kafka | **3.8.x / 4.x** | Log distribuido, replay, particionado |
| Cliente Kafka | Spring for Apache Kafka | **3.3.x** | `@KafkaListener`, transacciones, retry topics |
| Schema Registry | Confluent Schema Registry | 7.x (opcional) | Evolución de eventos con Avro/JSON Schema |
| Serialización | JSON (inicio) → Avro (etapa avanzada) | — | JSON simplifica debug en fases iniciales |

### 2.4 Seguridad

| Componente | Tecnología | Versión | Razón |
|-----------|-----------|---------|-------|
| AuthN/AuthZ | Spring Security | **6.4.x** | Estándar de facto, integración nativa |
| Identity Provider | Keycloak | **26.5.x** | OAuth2/OIDC completo, SSO, RBAC, multi-realm |
| Token format | JWT (RS256) | — | Stateless, verificable sin DB lookup |
| Hashing passwords | BCrypt (delegado a Keycloak) | — | Estándar |

### 2.5 IA (provider-agnostic)

| Componente | Tecnología | Versión | Razón |
|-----------|-----------|---------|-------|
| Abstracción IA | Spring AI | **1.0.3 (GA)** | `ChatClient` unifica OpenAI/Anthropic/Ollama/Bedrock |
| Vector DB (RAG futuro) | pgvector | extensión de Postgres | No requiere infra extra |
| Patrón provider | **Strategy Pattern** sobre `ChatModel` | — | Cambio de proveedor por configuración |

### 2.6 Observabilidad

| Componente | Tecnología | Versión | Razón |
|-----------|-----------|---------|-------|
| Métricas | Micrometer + Prometheus | — | Estándar Spring Boot |
| Visualización | Grafana | — | Dashboards |
| Logs | Logback + Loki | — | Stack ligero |
| Tracing | OpenTelemetry + Tempo/Jaeger | — | Trazas distribuidas entre servicios |
| Health | Spring Boot Actuator | — | `/actuator/health`, `/actuator/metrics` |

### 2.7 Infraestructura

| Componente | Tecnología | Razón |
|-----------|-----------|-------|
| Contenedores | Docker + Docker Compose | Local dev y staging |
| Orquestación (opcional) | Kubernetes (Kind/Minikube → cloud) | Etapa avanzada |
| CI/CD | GitHub Actions | Gratis, integrado con repo |
| Image registry | GitHub Container Registry | Gratis para repos privados |

### 2.8 Frontend (mínimo, para demo)

| Componente | Tecnología | Versión | Razón |
|-----------|-----------|---------|-------|
| Framework | Angular | **19.x** | Tu fuerte; ideal para demo profesional |
| State | Signals + NgRx Signal Store | — | Moderno, sin Redux clásico |
| HTTP | HttpClient + Interceptors | — | JWT injection automático |
| UI Kit | Angular Material o PrimeNG | — | Acelera demo sin distraer del backend |
| Auth | `angular-oauth2-oidc` | — | Integra con Keycloak |

### 2.9 Testing

| Tipo | Herramienta | Razón |
|------|-------------|-------|
| Unit | JUnit 5 + Mockito + AssertJ | Estándar |
| Integration | Spring Boot Test + Testcontainers | DB y Kafka reales en tests |
| Contract (avanzado) | Spring Cloud Contract / Pact | Garantiza compatibilidad entre servicios |
| Architecture | ArchUnit | Verifica que la arquitectura hexagonal NO se viole |
| API | RestAssured | Tests end-to-end de endpoints |
| Performance (opcional) | k6 / Gatling | Carga de endpoints críticos |

---

## 3. Arquitectura de alto nivel

### 3.1 Diagrama lógico (textual)

```
                          ┌──────────────────────────────────────┐
                          │       Frontend (Angular SPA)         │
                          └──────────────┬───────────────────────┘
                                         │ HTTPS
                                         ▼
                          ┌──────────────────────────────────────┐
                          │     API Gateway (Spring Cloud)       │
                          │   • Routing  • JWT validation        │
                          │   • Rate limit  • CORS  • Logging    │
                          └──┬─────┬─────┬─────┬─────┬─────┬─────┘
                             │     │     │     │     │     │
              ┌──────────────┘     │     │     │     │     └──────────────┐
              ▼                    ▼     ▼     ▼     ▼                    ▼
       ┌─────────────┐  ┌─────────────┐ │ ┌─────────────┐  ┌──────────────────┐
       │ auth-service│  │user-service │ │ │project-svc  │  │notification-svc  │
       │ (Keycloak   │  │ profiles    │ │ │ projects    │  │ email/push       │
       │  adapter)   │  │ teams       │ │ │ membership  │  │ subscriptions    │
       └─────┬───────┘  └─────┬───────┘ │ └─────┬───────┘  └────────┬─────────┘
             │                │         │       │                   │
             ▼                ▼         ▼       ▼                   ▼
       ┌─────────┐      ┌─────────┐  ┌──────┐ ┌─────────┐     ┌──────────┐
       │ auth_db │      │ user_db │  │      │ │project  │     │notif_db  │
       │  (PG)   │      │  (PG)   │  │      │ │  _db    │     │  (PG)    │
       └─────────┘      └─────────┘  │      │ └─────────┘     └──────────┘
                                     ▼      ▼
                              ┌─────────────┐  ┌──────────────────┐
                              │ task-service│  │   ai-service     │
                              │ tasks       │  │  Spring AI       │
                              │ subtasks    │  │  Strategy:       │
                              │ activity_log│  │  OpenAI/Anthr/   │
                              └──────┬──────┘  │  Ollama          │
                                     │         └────────┬─────────┘
                                     ▼                  │
                              ┌─────────────┐           │
                              │  task_db    │           │
                              │   (PG)      │           │
                              └─────────────┘           │
                                                        ▼
                                                ┌───────────────┐
                                                │ ai_cache_db   │
                                                │ (PG+pgvector) │
                                                └───────────────┘

                       ┌────────────────────────────────────────┐
                       │      Apache Kafka (event backbone)     │
                       │  topics: project.events, task.events,  │
                       │  user.events, ai.requests, ai.results  │
                       └────────────────────────────────────────┘
                              ▲          ▲          ▲
                              │          │          │ (todos los servicios
                              │          │          │  publican y consumen)
                       ┌──────┴──────┐ ┌─┴──────┐ ┌─┴──────────┐
                       │  Discovery  │ │ Config │ │  Keycloak  │
                       │  (Eureka)   │ │ Server │ │  (IDP)     │
                       └─────────────┘ └────────┘ └────────────┘

                       ┌────────────────────────────────────────┐
                       │  Observability stack                    │
                       │  Prometheus • Grafana • Loki • Tempo    │
                       └────────────────────────────────────────┘
```

### 3.2 Principios arquitectónicos rectores

1. **Database-per-service:** cada microservicio dueño absoluto de su esquema. Sin joins entre DBs.
2. **Eventual consistency entre servicios:** la consistencia fuerte vive **dentro** de cada bounded context.
3. **Event-driven first:** comunicación asíncrona por defecto; HTTP solo para queries de cliente o flujos sincrónicos imprescindibles.
4. **Hexagonal (ports & adapters) en cada microservicio:** dominio puro al centro, infraestructura en los bordes.
5. **DDD táctico:** entidades, value objects, agregados, repositorios, domain events.
6. **CQRS opcional** solo donde haya divergencia real entre lectura y escritura (probablemente en `task-service`).
7. **Outbox Pattern obligatorio** para publicar eventos de cambios de estado del dominio.
8. **API contract first:** OpenAPI 3 generado desde código, validado en CI.
9. **Stateless services:** ningún servicio guarda estado de sesión; todo va a token o cache externo.
10. **Idempotencia en consumers:** cada handler de evento debe tolerar re-entrega.

---

## 4. Patrones arquitectónicos aplicados

### 4.1 Hexagonal Architecture (por servicio)

Estructura de paquetes obligatoria en cada microservicio:

```
com.epm.<service>/
├── domain/                          # NÚCLEO — sin dependencias de Spring
│   ├── model/                       # entidades, value objects, agregados
│   ├── event/                       # domain events (POJOs)
│   ├── port/
│   │   ├── in/                      # use case interfaces (driving ports)
│   │   └── out/                     # repository / messaging interfaces (driven ports)
│   └── service/                     # domain services (lógica que no cae en una entidad)
│
├── application/                     # CASOS DE USO
│   ├── usecase/                     # implementaciones de port.in
│   └── handler/                     # event handlers internos
│
└── infrastructure/                  # ADAPTADORES — Spring vive acá
    ├── adapter/
    │   ├── in/
    │   │   ├── rest/                # controllers, DTOs, mappers
    │   │   └── messaging/           # Kafka consumers
    │   └── out/
    │       ├── persistence/         # JPA entities, repositories
    │       └── messaging/           # Kafka producers, outbox
    ├── config/                      # @Configuration classes
    └── security/                    # filters, JWT decoders
```

**Regla de oro:** `domain/` y `application/` **NO** importan Spring, JPA, ni nada de infra. Verificado con **ArchUnit** en CI.

### 4.2 Domain-Driven Design (estratégico)

Bounded contexts identificados:

| Bounded context | Microservicio | Agregado raíz | Lenguaje ubicuo |
|-----------------|---------------|---------------|-----------------|
| Identity | `auth-service` | `Account` | login, credential, token |
| User Management | `user-service` | `UserProfile`, `Team` | profile, member, role |
| Project Planning | `project-service` | `Project` | project, milestone, settings |
| Task Execution | `task-service` | `Task` | task, status, priority, deadline |
| Communication | `notification-service` | `Notification` | event, subscription, channel |
| AI Assistance | `ai-service` | `AiRequest` | prompt, completion, suggestion |

**Context map:** los servicios se relacionan vía **published language** (eventos versionados) — no comparten modelos.

### 4.3 Outbox Pattern

Problema: si guardás en DB y publicás en Kafka en transacciones distintas, podés perder eventos o publicarlos sin commit. Solución estándar:

```
┌────────────────────────────────────────────────────────┐
│  Transacción única en PostgreSQL                       │
│  ┌─────────────────┐      ┌────────────────────────┐  │
│  │ Cambio en tabla │  +   │ INSERT en outbox_event │  │
│  │   de dominio    │      │  (pendiente)           │  │
│  └─────────────────┘      └────────────────────────┘  │
└────────────────────────────────────────────────────────┘
                           │
                           ▼
        ┌──────────────────────────────────┐
        │ Outbox Relay (scheduled poller   │
        │   o Debezium CDC)                │
        │  Lee filas pendientes →          │
        │  publica a Kafka →               │
        │  marca como publicado            │
        └──────────────────────────────────┘
```

Implementación: scheduled poller en cada servicio (fase inicial) y migración a **Debezium** + Kafka Connect en fase avanzada.

### 4.4 Saga Pattern (orquestada vs coreografiada)

Para flujos que cruzan servicios (ej: "crear proyecto + asignar equipo + generar tareas con IA"):

- **Coreografía** (default): cada servicio reacciona a eventos. Más desacoplado.
- **Orquestación**: un orquestador centralizado decide los pasos. Solo si la lógica es compleja.

**Compensación obligatoria:** todo paso de una saga necesita un evento compensatorio (`TaskCreationFailed` → `ProjectCreationRolledBack`).

### 4.5 Circuit Breaker + Retry + Timeout (Resiliencia)

Resilience4j integrado en todos los clientes HTTP entre servicios y en clientes de IA:

- **Timeout:** 3-5s para llamadas internas, 30-60s para IA.
- **Retry:** 3 intentos con backoff exponencial + jitter.
- **Circuit Breaker:** abre tras 50% de fallos en ventana de 10 requests.
- **Bulkhead:** limita concurrencia por dependencia (especialmente IA).

### 4.6 API Gateway Pattern

`api-gateway` es el ÚNICO punto de entrada para clientes externos. Responsabilidades:

- Routing por path/header.
- Validación JWT (introspection o verificación local con JWKS de Keycloak).
- Rate limiting por usuario / IP (Redis backed).
- Request/response logging con `traceId`.
- CORS centralizado.
- Agregación opcional (BFF pattern para endpoints del frontend).

**NO hace:** lógica de negocio, transformación de datos, cacheo de respuestas (eso vive en cada servicio).

### 4.7 CQRS (selectivo)

Solo en `task-service` cuando el modelo de lectura difiere del de escritura (ej: vistas tipo Kanban con denormalización). Implementación inicial: vistas materializadas en Postgres. Avanzada: read model separado alimentado por eventos.

### 4.8 Strategy Pattern para IA

```java
// domain/port/out/AiAssistantPort.java
public interface AiAssistantPort {
    TaskGenerationResult generateTasks(ProjectContext ctx, String prompt);
    String summarize(ProjectContext ctx);
    List<Suggestion> suggest(ProjectContext ctx);
}

// infrastructure/adapter/out/ai/SpringAiChatAdapter.java
@Component
@Primary
public class SpringAiChatAdapter implements AiAssistantPort {
    private final ChatClient chatClient; // inyectado según provider activo
    // ...
}

// Provider seleccionado por configuración:
// spring.ai.provider=openai  ó  anthropic  ó  ollama
```

Cambio de proveedor = cambio de `application.yml` + dependencia. Sin tocar código de dominio.

### 4.9 Other patterns

- **Repository Pattern:** interfaces en `domain/port/out`, implementaciones JPA en `infrastructure`.
- **Factory Pattern:** creación de agregados complejos (ej: `ProjectFactory.createWithDefaults()`).
- **Specification Pattern:** consultas dinámicas en `task-service`.
- **Event Sourcing (opcional, fase avanzada):** solo en `activity_log` para audit trail completo.

---

## 5. Inventario de microservicios

### 5.1 `api-gateway`
**Responsabilidad:** punto único de entrada, routing, seguridad perimetral.
**Stack:** Spring Cloud Gateway 4.3 (reactive).
**Puerto:** 8080.
**DB:** ninguna.
**Dependencias:** Eureka, Config, Keycloak (JWKS).

### 5.2 `discovery-service` (Eureka Server)
**Responsabilidad:** registro y descubrimiento.
**Stack:** Spring Cloud Netflix Eureka Server.
**Puerto:** 8761.
**DB:** ninguna (estado en memoria).

### 5.3 `config-service`
**Responsabilidad:** configuración centralizada.
**Stack:** Spring Cloud Config Server con backend Git.
**Puerto:** 8888.
**DB:** ninguna (lee de repo Git).

### 5.4 `auth-service`
**Responsabilidad:** orquesta autenticación contra Keycloak, expone endpoints de registro/login que adaptan al cliente.
**Endpoints clave:** `/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`.
**DB:** `auth_db` (PG) — solo metadata local (último login, intentos fallidos, dispositivos confiables). **Las credenciales viven en Keycloak.**
**Eventos publicados:** `UserRegistered`, `UserLoggedIn`, `UserLockedOut`.

### 5.5 `user-service`
**Responsabilidad:** perfiles, preferencias, equipos, membresías.
**DB:** `user_db` (PG).
**Eventos publicados:** `ProfileUpdated`, `TeamCreated`, `MemberJoinedTeam`, `MemberLeftTeam`.
**Eventos consumidos:** `UserRegistered` (auth) → crea perfil vacío.

### 5.6 `project-service`
**Responsabilidad:** proyectos, configuración de proyecto, asignación de equipos.
**DB:** `project_db` (PG).
**Eventos publicados:** `ProjectCreated`, `ProjectUpdated`, `ProjectArchived`, `TeamAssignedToProject`.
**Eventos consumidos:** `TeamDeleted` → marca proyectos como huérfanos.

### 5.7 `task-service`
**Responsabilidad:** tareas, jerarquía, estados, prioridades, deadlines, activity log.
**DB:** `task_db` (PG).
**Eventos publicados:** `TaskCreated`, `TaskUpdated`, `TaskStatusChanged`, `TaskAssigned`, `TaskDeleted`.
**Eventos consumidos:** `ProjectArchived` → archiva tareas; `AiTasksGenerated` → ingesta tareas creadas por IA.

### 5.8 `ai-service`
**Responsabilidad:** integración con LLMs vía Spring AI, gestión de prompts, cache, control de costo.
**DB:** `ai_db` (PG + pgvector) — almacena requests, responses, embeddings para RAG futuro.
**Eventos publicados:** `AiTasksGenerated`, `AiSummaryGenerated`, `AiAnalysisCompleted`, `AiRequestFailed`.
**Eventos consumidos:** `AiAnalysisRequested` (desde cualquier servicio).
**Endpoints HTTP:** `POST /ai/tasks/generate`, `POST /ai/projects/{id}/summary`, `POST /ai/chat`.
**Particularidades:**
  - Rate limiting agresivo (cuota por usuario).
  - Cache de respuestas en Redis (key = hash de prompt + contexto).
  - Circuit breaker estricto.
  - Métricas de costo por request (tokens consumidos).

### 5.9 `notification-service`
**Responsabilidad:** suscripciones de usuario a eventos, envío de notificaciones (email, push, in-app).
**DB:** `notification_db` (PG).
**Eventos consumidos:** todos los eventos de dominio relevantes.
**Salidas:** SMTP (email), opcional FCM/APNs (push), WebSocket (in-app).

---

## 6. Comunicación entre servicios

### 6.1 Síncrona (HTTP/REST)

**Cuándo usar:**
- Requests del frontend (siempre vía gateway).
- Queries inter-servicio donde la respuesta es **necesaria inmediatamente** y no se puede modelar como evento.

**Reglas:**
- Cliente: **OpenFeign** declarativo, configurado con LoadBalancer del Spring Cloud.
- Siempre con timeout, retry y circuit breaker (Resilience4j).
- Headers obligatorios: `X-Request-ID` (UUID propagado), `Authorization` (JWT), `Accept: application/json`.
- Versionado por path: `/api/v1/...`.
- Errores estandarizados con **RFC 7807 Problem Details**.

### 6.2 Asíncrona (Kafka)

**Cuándo usar:**
- Notificación de cambios de estado del dominio (siempre).
- Workflows de larga duración (sagas).
- Side-effects de eventos (notificaciones, logs, analytics).

**Convenciones:**

| Aspecto | Convención |
|--------|-----------|
| Naming de topics | `<context>.<aggregate>.<event-type>` ej `project.task.created` |
| Particionado | Por `aggregateId` (garantiza orden por entidad) |
| Replicación | Factor 3 en producción, 1 en dev |
| Retención | 7 días eventos transitorios, infinito eventos auditables |
| Schema | JSON en fase 1; Avro + Schema Registry en fase avanzada |
| Versionado | Campo `eventVersion` en payload + naming `v1`, `v2` en topic si hay breaking change |
| Headers obligatorios | `event-id` (UUID), `event-type`, `event-version`, `trace-id`, `occurred-at` |

**Estructura de evento (envelope):**

```json
{
  "eventId": "uuid",
  "eventType": "ProjectCreated",
  "eventVersion": 1,
  "occurredAt": "2026-04-27T10:00:00Z",
  "aggregateId": "project-uuid",
  "aggregateType": "Project",
  "tenantId": "tenant-uuid",
  "traceId": "trace-uuid",
  "payload": { /* específico del evento */ }
}
```

### 6.3 Idempotencia en consumers

Cada consumer mantiene una tabla `processed_events(event_id PK, processed_at)`. Antes de procesar verifica; si existe, descarta. Inserción en la misma transacción que el side-effect.

### 6.4 Dead Letter Topics

Tras N reintentos fallidos, mensajes van a `<topic>.DLT`. Endpoint admin para reprocesar manualmente.

---

## 7. Seguridad

### 7.1 Modelo de identidad

- **Keycloak** es el IDP único.
- Cada cliente (frontend, mobile futuro) es un **client** registrado en Keycloak.
- **Realm único** para el SaaS, con grupos por tenant.
- **Multi-tenancy:** modelo de **shared schema, tenant_id por fila** (más simple para arrancar) con posibilidad de migrar a schema-per-tenant si se justifica.

### 7.2 Flujo de autenticación

```
Frontend → Keycloak: login (Authorization Code Flow + PKCE)
Keycloak → Frontend: access_token (JWT, 15 min) + refresh_token (8h)
Frontend → API Gateway: request con Bearer access_token
API Gateway → JWKS de Keycloak: valida firma (cacheado)
API Gateway → microservicio: forwarda con JWT + claims extraídos como headers
microservicio: valida claims, aplica @PreAuthorize
```

### 7.3 Autorización

- **RBAC:** roles `ADMIN`, `MANAGER`, `MEMBER`, `GUEST` (configurables en Keycloak).
- **Method-level security:** `@PreAuthorize("hasRole('MANAGER') and #project.ownerId == authentication.principal.id")`.
- **Resource-level:** verificación explícita de pertenencia (ej: ¿el user pertenece al team del project?).
- **Tenant isolation:** filtro automático por `tenantId` extraído del token (Hibernate filter o repository overrides).

### 7.4 Otras medidas

- HTTPS obligatorio en producción (cert-manager + Let's Encrypt en K8s).
- Headers de seguridad en Gateway: `Strict-Transport-Security`, `X-Content-Type-Options`, `Content-Security-Policy`.
- Rate limiting: 100 req/min por usuario, 10 req/min en endpoints de IA.
- Secrets fuera del código: variables de entorno → en producción **Vault** o **AWS Secrets Manager**.
- SQL injection: imposible si se usan repositorios JPA + queries parametrizadas (ArchUnit verifica que no haya `EntityManager.createNativeQuery` con strings concatenados).
- Audit log: todos los cambios sensibles publican evento → `notification-service` o servicio de auditoría dedicado los persiste.

---

## 8. Integración con IA — diseño detallado

### 8.1 Abstracción provider-agnostic

```java
// domain/port/out/
public interface ChatPort {
    ChatResponse chat(ChatRequest request);
}

public interface EmbeddingPort {
    float[] embed(String text);
}
```

```java
// infrastructure/adapter/out/ai/
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
public class OpenAiChatAdapter implements ChatPort { /* usa OpenAiChatModel */ }

@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "anthropic")
public class AnthropicChatAdapter implements ChatPort { /* usa AnthropicChatModel */ }

@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "ollama")
public class OllamaChatAdapter implements ChatPort { /* usa OllamaChatModel */ }
```

Cambio de proveedor = `ai.provider=anthropic` en Config Server. Cero rebuild.

### 8.2 Casos de uso priorizados

| Caso de uso | Endpoint | Modelo recomendado | Costo aproximado |
|-------------|----------|-------------------|------------------|
| Generar tareas desde descripción | `POST /ai/tasks/generate` | gpt-4o-mini / claude-haiku | bajo |
| Resumir proyecto | `POST /ai/projects/{id}/summary` | gpt-4o-mini | bajo |
| Sugerencias de mejora | `POST /ai/projects/{id}/suggestions` | gpt-4o / claude-sonnet | medio |
| Chat contextual | `POST /ai/chat` (streaming) | gpt-4o-mini | medio |
| Análisis de backlog | `POST /ai/projects/{id}/backlog-analysis` | gpt-4o / claude-sonnet | alto |

### 8.3 Gestión de prompts

- Prompts en archivos `.st` (Spring Template) versionados en repo.
- Variables inyectadas con Spring AI `PromptTemplate`.
- Prompt versioning: `prompt.task-generation.v2.st` — cambios mayores van a versión nueva.

### 8.4 Control de costo

- **Cache de respuestas** en Redis: key = SHA-256(provider + model + prompt + context). TTL configurable.
- **Cuotas por usuario:** N requests/día según plan.
- **Tracking de tokens:** persisten en `ai_db.ai_request_log` con costo calculado.
- **Modelo "cheap-first":** intentar con modelo barato; si la respuesta no pasa validación, escalar al caro.
- **Kill switch:** si gasto del día excede umbral, devolver error o usar fallback estático.

### 8.5 RAG (Retrieval-Augmented Generation) — fase avanzada

- Indexar tareas, comentarios y descripciones de proyecto como embeddings en `ai_db` (pgvector).
- Cuando el usuario pregunta algo, retrieve top-k chunks relevantes → inyectar en prompt.
- Mantiene a la IA "consciente" del estado del proyecto sin meter todo en el contexto.

---

## 9. Estructura del repositorio

**Decisión: monorepo** (más simple para aprender, reduce overhead de coordinación entre servicios).

```
intelligent-project-management-saas/
├── README.md
├── arquitectura.md          ← este documento
├── db.md                    ← diseño de bases de datos
├── docker-compose.yml       ← stack local completo
├── docker-compose.observability.yml
│
├── infra/
│   ├── keycloak/
│   │   └── realm-export.json
│   ├── kafka/
│   │   └── topics-init.sh
│   ├── postgres/
│   │   └── init-databases.sh
│   └── grafana/
│       └── dashboards/
│
├── config-repo/             ← repo Git separado en producción; carpeta acá en dev
│   ├── application.yml
│   ├── auth-service.yml
│   ├── user-service.yml
│   └── ...
│
├── shared/
│   ├── shared-events/       ← eventos versionados, JSON schemas
│   └── shared-security/     ← filtros JWT comunes
│
├── services/
│   ├── api-gateway/
│   ├── discovery-service/
│   ├── config-service/
│   ├── auth-service/
│   ├── user-service/
│   ├── project-service/
│   ├── task-service/
│   ├── ai-service/
│   └── notification-service/
│
├── frontend/                ← Angular SPA
│
├── docs/
│   ├── adr/                 ← Architecture Decision Records
│   ├── api/                 ← OpenAPI specs
│   └── diagrams/
│
└── .github/
    └── workflows/
        ├── ci-services.yml
        ├── ci-frontend.yml
        └── release.yml
```

Cada servicio tiene la misma estructura interna:

```
<service>/
├── pom.xml
├── Dockerfile
├── src/
│   ├── main/
│   │   ├── java/com/epm/<service>/
│   │   │   ├── domain/
│   │   │   ├── application/
│   │   │   └── infrastructure/
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/  ← Flyway scripts
│   └── test/
│       ├── java/...
│       └── resources/
└── README.md
```

---

## 10. Fases de ejecución (responsabilidades, sin tiempos)

> Estas fases son **secuenciales en dependencias**, pero cada una es un milestone cerrado. No hay deadlines: se avanza cuando la fase actual está SÓLIDA.

### **Fase 0 — Fundaciones**

**Responsabilidad:** dejar el repo y la infra local en condiciones de empezar a trabajar.

Entregables:
- Monorepo creado, README inicial, licencia.
- `docker-compose.yml` con: Postgres (multi-DB), Kafka + Zookeeper (o KRaft), Keycloak, Redis, Eureka, Config Server.
- Realm de Keycloak configurado y exportado.
- Topics de Kafka creados por script.
- Bases de datos vacías por servicio creadas.
- Convenciones de código documentadas (Checkstyle, formatter, EditorConfig).
- Plantilla Maven parent con dependencias compartidas (Spring Boot BOM, versiones unificadas).
- Plantilla de microservicio (un "hello world" con la estructura hexagonal completa).
- ArchUnit configurado y pasando.
- ADR-001: stack tecnológico. ADR-002: arquitectura hexagonal. ADR-003: monorepo.

**Salida:** `docker-compose up` levanta toda la infra. Servicios todavía no existen, pero la base está lista.

---

### **Fase 1 — Núcleo de plataforma (infra de servicios)**

**Responsabilidad:** los servicios infraestructurales que el resto necesita.

Entregables:
- `discovery-service`: Eureka funcionando, dashboard accesible.
- `config-service`: Config Server leyendo de `config-repo`, cifrado de propiedades sensibles.
- `api-gateway`: routing dinámico desde Eureka, validación JWT contra Keycloak, CORS, logging con `traceId`.
- Health endpoints en todos los servicios infra.
- Documentación de cómo agregar un nuevo servicio al sistema.

**Salida:** un cliente puede pegarle al Gateway con un JWT válido y el Gateway lo valida (aunque todavía no haya servicios de dominio detrás).

---

### **Fase 2 — Identidad y usuarios**

**Responsabilidad:** los usuarios pueden registrarse, loguearse y existir en el sistema.

Entregables:
- `auth-service` integrado con Keycloak: registro, login, refresh, logout.
- `user-service`: CRUD de perfiles, equipos, membresías.
- Outbox pattern implementado en ambos servicios.
- Eventos `UserRegistered`, `UserLoggedIn`, `TeamCreated`, `MemberJoinedTeam` publicándose.
- `user-service` consume `UserRegistered` y crea perfil.
- Tests unitarios + integración con Testcontainers.
- ArchUnit verificando hexagonal.
- OpenAPI documentado.

**Salida:** un usuario puede registrarse, loguearse, crear su equipo y ver su perfil. Toda interacción pasa por Gateway → Keycloak → servicio.

---

### **Fase 3 — Dominio de proyectos**

**Responsabilidad:** crear y gestionar proyectos.

Entregables:
- `project-service` con CRUD de proyectos, configuración, asignación de equipos.
- Eventos `ProjectCreated`, `ProjectUpdated`, `TeamAssignedToProject`.
- Verificación de pertenencia (un user solo ve proyectos de sus teams).
- Tests + OpenAPI.
- Frontend mínimo: pantalla de listado y creación de proyectos.

**Salida:** un usuario logueado puede crear proyectos, ver los suyos, asignarles un equipo. La UI demuestra el flujo end-to-end.

---

### **Fase 4 — Dominio de tareas**

**Responsabilidad:** la "carne" funcional del SaaS.

Entregables:
- `task-service`: CRUD, jerarquía padre-hijo (subtareas), estados, prioridades, deadlines.
- Activity log persistido (cada cambio relevante crea fila + publica evento).
- Eventos `TaskCreated`, `TaskUpdated`, `TaskStatusChanged`, `TaskAssigned`.
- Consumidor de `ProjectArchived` que archiva tareas en cascada.
- Vistas materializadas para tablero Kanban (CQRS-light).
- Frontend: tablero Kanban con drag & drop, vista lista, edición.

**Salida:** un equipo puede gestionar su trabajo completo: crear proyecto, asignar equipo, crear tareas, moverlas entre estados, ver historial.

---

### **Fase 5 — IA (provider-agnostic)**

**Responsabilidad:** integrar IA como diferenciador real, no como gimmick.

Entregables:
- `ai-service` con Spring AI configurado.
- Strategy pattern implementado: 3 adapters (OpenAI, Anthropic, Ollama).
- Endpoint `POST /ai/tasks/generate`: dada descripción libre, devuelve N tareas estructuradas.
- Endpoint `POST /ai/projects/{id}/summary`: resumen del estado del proyecto.
- Endpoint `POST /ai/chat` con streaming SSE.
- Cache de respuestas en Redis.
- Rate limiting por usuario.
- Tracking de tokens y costo en `ai_db`.
- Circuit breaker estricto.
- Frontend: botón "generar tareas con IA" en la pantalla de proyecto.
- Evento `AiTasksGenerated` consumido por `task-service` para crear las tareas.

**Salida:** la demo principal funciona: usuario describe un proyecto en lenguaje natural → IA genera tareas → aparecen en el tablero.

---

### **Fase 6 — Notificaciones**

**Responsabilidad:** mantener al usuario informado.

Entregables:
- `notification-service` con suscripciones por evento.
- Adapter SMTP para email (MailHog en local, SendGrid/SES en prod).
- WebSocket para notificaciones in-app real-time.
- Templates de email versionados.
- Frontend: campana con notificaciones no leídas, preferencias de notificación.

**Salida:** cuando algo relevante pasa (te asignan tarea, comentan en tu proyecto, IA terminó análisis), recibís notificación.

---

### **Fase 7 — Resiliencia y observabilidad**

**Responsabilidad:** que el sistema sea presentable y debuggeable.

Entregables:
- Resilience4j en TODOS los clientes Feign y todos los adapters de IA.
- Métricas Micrometer expuestas en `/actuator/prometheus`.
- Distributed tracing con OpenTelemetry — traceId propagado en HTTP y Kafka headers.
- Stack de observabilidad: Prometheus + Grafana + Loki + Tempo en `docker-compose.observability.yml`.
- Dashboards Grafana pre-configurados (latencia por servicio, throughput de Kafka, errores 5xx, costo de IA).
- Health checks customizados (DB, Kafka, Redis, Keycloak).
- Logs estructurados (JSON) con campos estándar: `service`, `traceId`, `userId`, `tenantId`.

**Salida:** abrís Grafana y ves el sistema funcionando en tiempo real. Una request te la podés seguir desde el frontend hasta cada servicio en Tempo.

---

### **Fase 8 — Testing serio**

**Responsabilidad:** que el proyecto resista una review técnica de un senior.

Entregables:
- Cobertura unit > 80% en capa de dominio y aplicación.
- Tests de integración con Testcontainers en cada servicio (DB + Kafka reales).
- Tests de arquitectura con ArchUnit verificando reglas hexagonales.
- Tests de contrato (Spring Cloud Contract) entre servicios que se comunican vía HTTP.
- Tests E2E del flujo crítico (registro → crear proyecto → IA genera tareas → mover a done).
- Mutation testing con PIT en módulos críticos.
- Reportes de cobertura publicados en CI.

**Salida:** podés mostrar el proyecto en una entrevista y nadie se te ríe del testing.

---

### **Fase 9 — Containerización y CI/CD**

**Responsabilidad:** deploy reproducible y automatizado.

Entregables:
- Dockerfile multi-stage por servicio (build + runtime con JRE alpine, imagen final < 250MB).
- Docker Compose completo (dev) y override files por entorno.
- GitHub Actions:
  - Pipeline por servicio: build → test (con Testcontainers) → static analysis (SonarCloud) → build image → push GHCR.
  - Pipeline frontend: build → test → lighthouse → deploy.
  - Release pipeline: tag → release notes auto → publish images con tag de versión.
- Versionado semántico automatizado.
- Análisis de vulnerabilidades de imágenes (Trivy).

**Salida:** push a `main` = imágenes nuevas en registry. Push de tag = release.

---

### **Fase 10 — Kubernetes (opcional)**

**Responsabilidad:** llevarlo a una orquestación real.

Entregables:
- Manifiestos K8s o Helm chart por servicio.
- Kind / Minikube para local; opcional cluster real (DigitalOcean, Linode, GKE free tier).
- Kustomize/overlays por entorno.
- ConfigMaps + Secrets (sealed-secrets si se publica).
- HPA (autoscaling) en servicios stateless.
- Ingress con cert-manager + Let's Encrypt.
- ArgoCD opcional para GitOps.

**Salida:** "este es el repo, este es el cluster, así se deploya" en una sola línea.

---

### **Fase 11 — Pulido y presentación**

**Responsabilidad:** que el proyecto venda solo.

Entregables:
- README maestro con: pitch, screenshots, GIFs del producto, links a demo, diagramas.
- ADRs completos en `docs/adr/`.
- OpenAPI publicado (Swagger UI o ReDoc).
- Demo grabada (5 min): registro → crear proyecto → IA genera tareas → tablero → notificación.
- Postman/Bruno collection.
- Sección "tradeoffs y decisiones" en docs.
- Sección "qué haría diferente con tiempo/equipo" — humildad técnica.
- Deploy demo accesible públicamente (opcional pero recomendado).

**Salida:** un link mostrable en LinkedIn / CV / entrevistas.

---

## 11. Convenciones de código y proceso

### 11.1 Git

- **Branch model:** trunk-based con feature branches cortos. `main` siempre deployable.
- **Commits:** Conventional Commits (`feat:`, `fix:`, `chore:`, `refactor:`, `test:`, `docs:`).
- **PRs (autoreview en proyecto solo):** checklist obligatorio antes de merge — tests pasan, ArchUnit pasa, OpenAPI actualizado, ADR si hay decisión arquitectónica.

### 11.2 Naming

- Paquetes Java: `com.epm.<service>` (epm = Enterprise Project Management).
- Clases: PascalCase con sufijos claros — `*UseCase`, `*Controller`, `*Repository`, `*Adapter`, `*Event`.
- Eventos: PascalCase, en pasado — `ProjectCreated`, NO `CreateProject`.
- Topics Kafka: kebab-case con namespacing — `project.task.created`.
- Tablas SQL: snake_case plural — `users`, `task_assignments`.

### 11.3 Documentación

- Cada servicio: README propio con qué hace, cómo correrlo aislado, cómo testearlo.
- Cada decisión arquitectónica relevante: ADR en `docs/adr/NNN-titulo.md`.
- API: OpenAPI generado desde código (springdoc-openapi).

### 11.4 Calidad

- SonarQube/SonarCloud configurado: deuda técnica visible, gates en CI.
- Checkstyle + Spotless para formato uniforme.
- Pre-commit hooks (Husky-like via Maven plugins) que corren format y tests rápidos.

---

## 12. Decisiones arquitectónicas con tradeoffs

### 12.1 Microservicios vs monolito modular

**Elegido:** microservicios desde el inicio.
**Tradeoff aceptado:** complejidad operacional alta para un proyecto solo. **Justificación:** el objetivo es APRENDER arquitectura distribuida; un modular monolith no entrenaría las skills que querés mostrar.
**Costo:** debugging más complejo, latencia de red, eventual consistency.
**Mitigación:** observabilidad desde fase 7, no antes.

### 12.2 Eureka vs Consul/Kubernetes Service Discovery

**Elegido:** Eureka.
**Tradeoff aceptado:** Eureka está en mantenimiento, no en desarrollo activo.
**Justificación:** integración nativa con Spring Cloud, simplicidad, ideal para aprender el concepto.
**Cuándo migrar:** si vas a fase 10 (K8s), service discovery nativo de K8s reemplaza Eureka.

### 12.3 Kafka vs RabbitMQ

**Elegido:** Kafka.
**Tradeoff aceptado:** mayor complejidad operacional, curva de aprendizaje.
**Justificación:** event sourcing real, replay, particionado, más demandado en empresas grandes.
**Costo:** más recursos en local (Zookeeper o KRaft, brokers).

### 12.4 Database-per-service vs DB compartida

**Elegido:** database-per-service.
**Tradeoff aceptado:** no podés hacer joins entre dominios; necesitás eventual consistency.
**Justificación:** es EL principio de microservicios; sin esto no hay aprendizaje real.
**Mitigación:** eventos de dominio + read models cuando hace falta vista cruzada.

### 12.5 IA provider-agnostic vs lock-in en uno

**Elegido:** agnostic con Strategy.
**Tradeoff aceptado:** no aprovechás features específicos de cada proveedor (ej: OpenAI Assistants API).
**Justificación:** demuestra ports & adapters real, te protege de cambios de pricing.
**Mitigación:** los adapters pueden exponer features extra vía interfaces específicas si surge la necesidad.

### 12.6 Spring Cloud Config con Git vs Consul/Vault

**Elegido:** Spring Cloud Config con Git.
**Tradeoff aceptado:** menos features que Consul/Vault.
**Justificación:** simplicidad, history nativo, branching para entornos.
**Cuándo migrar:** si hay secrets reales en producción, mover secrets a Vault y dejar config no-sensible en Config.

### 12.7 OpenFeign vs WebClient

**Elegido:** OpenFeign para inter-service, WebClient solo donde haya streaming/reactive real.
**Tradeoff aceptado:** Feign es bloqueante por default.
**Justificación:** declarativo, código más limpio, integración con Resilience4j vía annotations. Java 21 + virtual threads mitigan el costo del modelo bloqueante.

---

## 13. Riesgos identificados y mitigaciones

| Riesgo | Probabilidad | Impacto | Mitigación |
|--------|-------------|---------|------------|
| Costo de IA descontrolado | Alta | Medio | Cache + cuotas + kill switch + tracking |
| Eventual consistency confunde al usuario | Media | Alto | UI muestra estado optimista + reconciliación |
| Schema evolution rompe consumers | Media | Alto | Schema Registry + reglas de compatibilidad backward |
| Performance de N microservicios en local | Alta | Medio | Profiles para correr subset de servicios; Tilt opcional |
| Keycloak se vuelve cuello de botella | Baja | Alto | Cache de JWKS en Gateway; opcional offline validation |
| Outbox poller no escala | Baja | Medio | Migrar a Debezium CDC en fase avanzada |
| Sin tests reales = bugs en demo | Alta | Alto | Fase 8 dedicada exclusivamente a testing serio |

---

## 14. Definición de "hecho" del proyecto

El proyecto se considera al **100%** cuando:

1. Las 11 fases están entregadas con sus criterios cumplidos.
2. Un usuario nuevo puede levantar todo con `docker-compose up` siguiendo el README en menos de 15 minutos.
3. El flujo crítico (registro → proyecto → IA → tareas) funciona end-to-end sin intervención manual.
4. La cobertura de tests cumple los gates de CI.
5. Hay al menos 10 ADRs documentando decisiones reales del proyecto.
6. Una persona técnica externa puede revisar el repo y entender la arquitectura sin que vos expliques nada.
7. La demo grabada de 5 minutos existe y muestra valor real.

**Lo que NO entra en el 100%:** alta disponibilidad real (multi-región), compliance (SOC2, GDPR completo), pruebas de carga masivas. Esos son features de un equipo de plataforma, no de un proyecto de aprendizaje.

---

## 15. Próximos pasos inmediatos

1. **Leer este documento entero, dos veces.** Anotar dudas o desacuerdos.
2. **Leer `db.md`** — diseño de bases de datos por servicio.
3. **Validar conmigo cualquier decisión que no te cierre.** Discutir tradeoffs antes de codear.
4. **Arrancar Fase 0** con `/sdd-new fase-0-fundaciones` para que el SDD nos guíe formalmente.
5. **NO escribir código de servicios todavía.** La fase 0 es solo infra y andamiaje.

---

> *Este documento es vivo. Cada decisión que cambie debe reflejarse acá y en un ADR.*
