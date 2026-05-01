# ADR-003: Monorepo

## Estado
Accepted

## Fecha
2026-05-01

## Contexto

El sistema está compuesto por 9 microservicios, un frontend Angular, librerías compartidas (eventos versionados, filtros de seguridad) y configuración de infraestructura. Se necesita decidir cómo organizar el código fuente en repositorios Git.

El contexto es un proyecto de aprendizaje con un desarrollador único. Las decisiones priorizan la simplicidad operacional y la capacidad de hacer cambios coordinados entre servicios.

## Alternativas consideradas

### Polyrepo (un repositorio por servicio)
Cada microservicio en su propio repositorio Git independiente.

**Pros:** autonomía total por servicio. Pipelines de CI completamente independientes. Equipos separados pueden trabajar sin coordinación.

**Contras:** cambios que afectan múltiples servicios (ej: actualizar el schema de un evento compartido) requieren múltiples PRs coordinados. Difícil mantener consistencia de versiones de dependencias. Overhead operacional alto para un desarrollador solo. Compartir código entre servicios requiere publicar librerías a un registry.

### Multi-repo con submódulos Git
Repositorio principal con submódulos apuntando a repos de cada servicio.

**Pros:** algo de coordinación posible desde el repo principal.

**Contras:** los submódulos de Git son notoriamente difíciles de manejar. Complejidad sin beneficio real para este caso.

### Monorepo ← elegido
Todos los servicios, frontend e infraestructura en un único repositorio Git.

**Pros:** cambios coordinados en un solo PR (ej: actualizar `shared-events` y todos los consumers juntos). CI puede verificar compatibilidad entre servicios en cada push. Dependencias compartidas versionadas de forma centralizada en el parent POM. Un solo lugar para clonar y levantar todo el sistema.

**Contras:** el repositorio crece con el tiempo. Sin path filtering en CI, los pipelines se vuelven lentos (todos corren en cada push). Requiere disciplina para mantener la independencia lógica entre servicios.

## Decisión

**Monorepo** con la estructura definida en `arquitectura.md` sección 9.

Path filtering en GitHub Actions garantiza que cada servicio solo se testea cuando cambian sus archivos:

```yaml
on:
  push:
    paths:
      - 'services/**'
      - 'shared/**'
      - 'build-tools/**'
      - 'pom.xml'
```

Los servicios siguen siendo **lógicamente independientes**: cada uno tiene su propia base de datos, su propio ciclo de deployment (en fases avanzadas) y no comparte código de dominio con otros servicios. El monorepo es una decisión de organización de código fuente, no de arquitectura.

## Consecuencias

**Positivas:**
- Un solo `git clone` + `docker-compose up` levanta todo el sistema.
- Cambios en `shared-events` (schemas de eventos) se propagan y verifican en el mismo PR.
- El parent POM centraliza versiones de dependencias — un solo lugar para actualizar Spring Boot o Spring Cloud.
- CI puede detectar breaking changes entre productor y consumer de eventos antes de mergear.

**Negativas:**
- El repositorio acumulará mucho código con el tiempo.
- Sin discipline de path filtering, los pipelines de CI podrían volverse lentos.

**Mitigaciones:**
- Path filtering configurado desde el día 1 en `.github/workflows/ci-services.yml`.
- En una evolución futura hacia equipos independientes, la migración a polyrepo es directa: cada carpeta de `services/` se convierte en su propio repo.
