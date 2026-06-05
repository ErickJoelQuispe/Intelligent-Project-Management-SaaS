# Intelligent Project Management SaaS

> Plataforma SaaS multi-tenant de gestión de proyectos con asistencia de IA, construida como un sistema distribuido orientado a eventos sobre microservicios Java.

![CI](https://github.com/ErickJ10X/intelligent-project-management-saas/actions/workflows/ci-services.yml/badge.svg)

---

## Stack tecnológico

| Capa | Tecnología |
|------|-----------|
| Lenguaje | Java 21 LTS |
| Framework | Spring Boot 3.5 + Spring Cloud 2025.0 |
| API Gateway | Spring Cloud Gateway |
| Service Discovery | Eureka |
| Config centralizado | Spring Cloud Config |
| Mensajería | Apache Kafka (KRaft) |
| Base de datos | PostgreSQL 16 (database-per-service) |
| Cache | Redis 7.4 |
| Seguridad | Keycloak 26 (OAuth2/OIDC) |
| IA | Spring AI 1.0 (provider-agnostic) |
| Frontend | Angular 19 |
| Observabilidad | Prometheus + Grafana + Loki + Tempo |
| CI/CD | GitHub Actions |

---

## Cómo levantar el entorno local

### Prerequisitos

- Docker Engine 24+ o Docker Desktop 4+
- Java 21 JDK (recomendado via [SDKMAN](https://sdkman.io/))
- Maven 3.9+

### Pasos

```bash
# 1. Clonar el repositorio
git clone https://github.com/ErickJ10X/intelligent-project-management-saas
cd intelligent-project-management-saas

# 2. Copiar variables de entorno
cp .env.example .env
# Editar .env si querés cambiar passwords (opcional en local)

# 3. Levantar infraestructura
docker-compose up -d

# 4. Crear los topics de Kafka (primera vez)
./infra/kafka/topics-init.sh

# 5. Verificar que todo está OK
docker-compose ps
# Todos los servicios deben estar "healthy"
```

### URLs locales

| Servicio | URL | Credenciales |
|----------|-----|-------------|
| Keycloak | http://localhost:8180 | admin / admin |
| Eureka Dashboard | http://localhost:8761 | — |
| Config Server | http://localhost:8888 | — |
| API Gateway | http://localhost:8080 | — |
| Auth Service | http://localhost:8081 | — |
| User Service | http://localhost:8082 | — |
| PostgreSQL | localhost:5432 | epm_admin / changeme |
| Kafka | localhost:9092 | — |
| Redis | localhost:6379 | — |

### Bases de datos de test

Los tests de persistencia requieren bases de datos de test en la instancia PostgreSQL en ejecución.
Crearlas la primera vez (solo una vez por volumen):

```bash
docker exec epm-postgres psql -U epm_admin -d postgres -c "CREATE DATABASE auth_test;"
docker exec epm-postgres psql -U epm_admin -d postgres -c "CREATE DATABASE user_test;"
```

### Keycloak Client Secret

Antes de levantar auth-service, obtener el client secret desde Keycloak Admin UI:

> **Keycloak Admin** → realm `epm` → Clients → `epm-backend` → Credentials → Client Secret

Luego agregar al `.env`:

```env
KEYCLOAK_CLIENT_SECRET=<valor-copiado>
```

---

## Fases del proyecto

| Fase | Descripción | Estado |
|------|-------------|--------|
| **0** | Fundaciones — repo, infra local, plantilla hexagonal | ✅ Completo |
| **1** | Núcleo de plataforma — Eureka, Config, Gateway | ✅ Completo |
| **2** | Identidad y usuarios — auth-service, user-service | ✅ Completo |
| **3** | Dominio de proyectos — project-service | ✅ Completo |
| **4** | Dominio de tareas — task-service | ✅ Completo |
| **5** | IA — DeepSeek via Spring AI (ai-service, Redis cache, Kafka outbox) | ✅ Completo |
| **6** | Notificaciones — WebSocket/STOMP, email Thymeleaf, preferencias | ✅ Completo |
| **7** | Resiliencia y observabilidad — Prometheus, Grafana, Loki, Tempo, R4J | ✅ Completo |
| **8** | Testing serio | ⏳ Pendiente |
| **9** | Containerización y CI/CD | ⏳ Pendiente |
| **10** | Kubernetes (opcional) | ⏳ Pendiente |
| **11** | Pulido y presentación | ⏳ Pendiente |

---

## Arquitectura

Ver [`arquitectura.md`](./arquitectura.md) para el documento maestro de arquitectura.

Ver [`db.md`](./db.md) para el diseño de bases de datos.

Ver [`docs/adr/`](./docs/adr/) para las Architecture Decision Records.

---

## Convenciones

Ver [`docs/conventions.md`](./docs/conventions.md).

Ver [`docs/angular-routing-guide.md`](./docs/angular-routing-guide.md) para la convención de rutas absolutas vs. relativas en Angular.
