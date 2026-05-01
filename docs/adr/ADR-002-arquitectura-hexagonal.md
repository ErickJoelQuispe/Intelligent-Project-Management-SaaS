# ADR-002: Arquitectura hexagonal en cada microservicio

## Estado
Accepted

## Fecha
2026-05-01

## Contexto

Cada microservicio necesita una estructura interna que permita:
1. Testear la lógica de negocio sin levantar Spring, bases de datos ni Kafka.
2. Cambiar detalles de infraestructura (proveedor de IA, motor de persistencia, broker de mensajería) sin modificar el dominio.
3. Verificar automáticamente en CI que las reglas de dependencia no se violan con el tiempo.

El riesgo concreto sin una arquitectura explícita: con el tiempo, los controllers empiezan a importar repositorios JPA directamente, los servicios de dominio importan clases de Spring, y el código se vuelve imposible de testear unitariamente.

## Alternativas consideradas

### Layered Architecture (Controller → Service → Repository)
La arquitectura en capas tradicional de Spring.

**Pros:** familiar, simple de entender, menos boilerplate inicial.

**Contras:** las capas solo restringen la dirección hacia abajo, pero el dominio sigue dependiendo de la capa de datos (JPA entities, repositorios de Spring Data). No hay separación real entre lógica de negocio e infraestructura. Difícil de testear sin contexto de Spring.

### Clean Architecture (Uncle Bob)
Círculos concéntricos con la regla de dependencia hacia adentro.

**Pros:** separación clara, dominio puro.

**Contras:** introduce conceptos adicionales (Interactors, Presenters, Boundaries) que agregan complejidad sin beneficio claro para microservicios pequeños. Hexagonal cubre los mismos objetivos con menos capas.

### Hexagonal Architecture (Ports & Adapters) ← elegida
Dominio al centro, puertos como interfaces, adaptadores como implementaciones.

**Pros:** dominio completamente libre de dependencias de framework. Puertos driving (in) y driven (out) hacen explícito qué entra y qué sale del dominio. ArchUnit puede verificar las reglas automáticamente.

**Contras:** más boilerplate inicial (adapters, mappers entre domain model y JPA entities). Curva de aprendizaje para quien no conoce el patrón.

## Decisión

**Arquitectura hexagonal en todos los microservicios**, con la siguiente estructura de paquetes obligatoria:

```
com.epm.<service>/
├── domain/          ← sin dependencias de Spring ni JPA
│   ├── model/
│   ├── event/
│   └── port/
│       ├── in/      ← driving ports (use case interfaces)
│       └── out/     ← driven ports (repository, messaging interfaces)
├── application/     ← implementa ports.in, usa ports.out
│   └── usecase/
└── infrastructure/  ← Spring, JPA, Kafka viven acá
    ├── adapter/
    │   ├── in/rest/
    │   ├── in/messaging/
    │   ├── out/persistence/
    │   └── out/messaging/
    └── config/      ← único lugar con @Bean y wiring
```

Las reglas se verifican con **ArchUnit** en cada build de CI:
- `domain` no importa `org.springframework.*`
- `domain` no importa `jakarta.persistence.*`
- `application` no importa `infrastructure`
- Dirección de dependencias: `infrastructure` → `application` → `domain`

## Consecuencias

**Positivas:**
- Los use cases son clases Java puras — testeables con JUnit + Mockito sin Spring context.
- Cambiar de PostgreSQL a otro motor de persistencia = reescribir solo los adapters de persistence.
- Cambiar de Kafka a otro broker = reescribir solo los adapters de messaging.
- ArchUnit actúa como guardián automático: si alguien viola una regla, el build falla.

**Negativas:**
- Más archivos por servicio (interfaces de ports, adapters, mappers).
- La clase `UseCaseConfig` con los `@Bean` puede crecer si hay muchos use cases.

**Mitigaciones:**
- `services/service-template` es la plantilla base — copiar y renombrar paquetes para cada servicio nuevo.
- Los mappers entre domain model y JPA entities son mecánicos; se pueden generar con MapStruct en fases avanzadas.
