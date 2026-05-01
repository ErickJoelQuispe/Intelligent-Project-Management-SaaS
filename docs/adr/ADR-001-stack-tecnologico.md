# ADR-001: Stack tecnológico

## Estado
Accepted

## Fecha
2026-05-01

## Contexto

Se necesita elegir el stack tecnológico para construir una plataforma SaaS multi-tenant de gestión de proyectos con asistencia de IA. El sistema está compuesto por múltiples microservicios que deben comunicarse de forma síncrona y asíncrona, con un frontend de demostración.

El objetivo es doble: construir un producto funcional Y demostrar dominio sobre arquitectura distribuida en un contexto de aprendizaje. Las decisiones priorizan claridad arquitectónica y presentabilidad por sobre velocidad de entrega.

## Alternativas consideradas

### Lenguaje y framework de aplicación

| Alternativa | Pros | Contras |
|-------------|------|---------|
| **Java 21 + Spring Boot 3.5** | Ecosistema Spring Cloud integrado (discovery, config, gateway, security). Virtual threads en Java 21 mitigan el costo del modelo bloqueante. Tooling maduro, ampliamente demandado. | Mayor consumo de memoria por servicio vs Go/Rust. Startup más lento (mitigable con GraalVM). |
| Go + gRPC | Muy bajo consumo de memoria. Binarios pequeños. | Ecosistema de microservicios menos integrado. Curva de aprendizaje alta para el objetivo. |
| Node.js + NestJS | Rápido para prototipar. Mismo lenguaje que el frontend. | Menos adecuado para sistemas distribuidos complejos con JVM. Concurrencia más compleja. |
| Quarkus | Startup nativo muy rápido. Compatible con el ecosistema Java. | Menos maduro que Spring Boot. Menor adopción empresarial al momento de la decisión. |

### Mensajería

| Alternativa | Pros | Contras |
|-------------|------|---------|
| **Apache Kafka** | Log distribuido con replay. Retención configurable. Particionado por aggregateId garantiza orden. Muy demandado en empresas grandes. | Mayor complejidad operacional. Más recursos en local. |
| RabbitMQ | Más simple de operar. Buen soporte de routing. | Destruye mensajes al consumirlos — sin replay nativo. Menos adecuado para event sourcing. |
| Redis Streams | Ya está en el stack. Simple. | No es su caso de uso principal. Menos features para sistemas distribuidos complejos. |

### Identity Provider

| Alternativa | Pros | Contras |
|-------------|------|---------|
| **Keycloak** | OAuth2/OIDC completo. SSO, RBAC, multi-realm. Self-hosted, sin costo por usuario. | Consume más memoria (~512MB). Curva de configuración inicial. |
| Auth0 | SaaS, zero operaciones. Excelente DX. | Costo por usuario en producción. Lock-in en proveedor externo. |
| Implementación propia | Control total. | Altísimo riesgo de bugs de seguridad. No se justifica cuando existen soluciones probadas. |

## Decisión

**Java 21 LTS + Spring Boot 3.5 + Spring Cloud 2025.0 + Apache Kafka + Keycloak 26.**

Stack completo documentado en `arquitectura.md` sección 2.

## Consecuencias

**Positivas:**
- Spring Cloud cubre service discovery, config centralizada, API gateway y seguridad de forma integrada y consistente.
- Java 21 con virtual threads elimina el penalty del modelo bloqueante en llamadas I/O.
- Kafka permite replay de eventos, múltiples consumers independientes y audit trail persistente.
- Keycloak delega toda la complejidad de autenticación a un sistema probado y auditado.

**Negativas:**
- Mayor consumo de memoria por servicio que alternativas como Go (~256MB por servicio JVM vs ~20MB en Go).
- Startup más lento sin GraalVM nativo (mitigable, no crítico en este contexto).
- Kafka agrega complejidad operacional y más recursos en el entorno local.
- Keycloak requiere configuración inicial no trivial.

**Mitigaciones:**
- Perfiles de Docker Compose para levantar subconjuntos de servicios en local.
- GraalVM nativo queda como mejora futura si el startup time se vuelve crítico.
