# ADR-004: API Gateway con Spring Cloud Gateway

## Estado
Accepted

## Fecha
2026-05-01

## Contexto

El sistema necesita un único punto de entrada para todos los clientes externos. Sin este componente, cada microservicio debería:
- Exponer su propio puerto al exterior
- Implementar validación de JWT de forma independiente
- Configurar CORS por separado
- Generar su propio `traceId`

Eso es duplicación de infraestructura en cada servicio y una superficie de ataque enorme.

## Alternativas consideradas

### Kong Gateway
**Pros:** muy completo, plugins para rate limiting, autenticación, transformaciones. Ampliamente usado en producción.

**Contras:** requiere su propia base de datos (PostgreSQL o Cassandra) para el modo clásico. Configuración vía Admin API o archivos declarativos — fuera del ecosistema Spring. Agregar lógica custom requiere plugins en Lua o Go.

### nginx / nginx-proxy
**Pros:** extremadamente liviano y performante. Conocido por la mayoría de los equipos.

**Contras:** no integra con Eureka — las rutas son estáticas. Para routing dinámico necesitás consul-template o similar. La validación de JWT requiere módulos externos (`nginx-auth-request`). Sin integración nativa con Spring Security.

### AWS API Gateway
**Pros:** zero operaciones, escalado automático, integración con Cognito y Lambda.

**Contras:** lock-in total en AWS. Costo por millón de requests. No tiene sentido para un entorno de desarrollo local ni para demostrar dominio de arquitectura distribuida.

### Spring Cloud Gateway ← elegido
**Pros:** integración nativa con Eureka (routing dinámico sin configuración de rutas estáticas). Spring Security reactivo con soporte OAuth2 Resource Server de primera clase. Filtros globales en Java puro. Sin dependencias externas adicionales.

**Contras:** basado en WebFlux (reactivo) — modelo de programación distinto al MVC que usan los microservicios de dominio. Requiere entender Reactor para escribir filtros custom.

## Decisión

**Spring Cloud Gateway 4.x** como único punto de entrada al sistema.

Responsabilidades asignadas al Gateway:

| Responsabilidad | Implementación |
|-----------------|----------------|
| Routing dinámico | Discovery Locator via Eureka — automático, sin rutas hardcodeadas |
| Validación JWT | OAuth2 Resource Server con JWKS cacheado de Keycloak |
| CORS | `globalcors` centralizado para `localhost:4200` |
| Trazabilidad | `TraceIdFilter` — genera o propaga `X-Request-ID` en cada request |

Responsabilidades explícitamente **NO asignadas** al Gateway:

- Lógica de negocio
- Transformación de payloads
- Cacheo de respuestas (vive en cada servicio)
- Autorización a nivel de recurso (vive en cada servicio)

## Consecuencias

**Positivas:**
- Los microservicios de dominio no saben nada de JWT ni de autenticación. Si el request llegó del Gateway, ya está autenticado.
- Agregar un nuevo microservicio al sistema es automático: se registra en Eureka y el Gateway empieza a enrutarle tráfico sin ningún cambio de configuración.
- CORS configurado en un solo lugar — nunca más "olvidé configurar CORS en el servicio X".
- El `traceId` se propaga a todos los microservicios, lo que permite correlacionar logs en Grafana/Loki.

**Negativas:**
- El Gateway es el único componente del sistema que usa WebFlux. Todos los demás usan Spring MVC bloqueante con virtual threads. Esta asimetría puede confundir a alguien que no lo sepa.
- Es un punto único de fallo — si el Gateway cae, el sistema entero deja de recibir tráfico externo. Mitigación: múltiples instancias del Gateway detrás de un load balancer en producción.

**Regla de oro:**
> Si la lógica que querés agregar al Gateway requiere conocer el modelo de dominio, no va en el Gateway. Va en el microservicio correspondiente.
