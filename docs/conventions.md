# Convenciones de codigo y proceso

Este documento resume las convenciones principales. La fuente de verdad es `arquitectura.md` (seccion 11).

## Git
- Branch model: trunk-based con feature branches cortos; `main` siempre deployable.
- Commits: Conventional Commits (`feat:`, `fix:`, `chore:`, `refactor:`, `test:`, `docs:`).
- PR checklist: tests pasan, ArchUnit pasa, OpenAPI actualizado, ADR si hay decision arquitectonica.

## Naming
- Paquetes Java: `com.epm.<service>`.
- Clases: PascalCase con sufijos claros (`*UseCase`, `*Controller`, `*Repository`, `*Adapter`, `*Event`).
- Eventos: PascalCase en pasado (`ProjectCreated`).
- Topics Kafka: kebab-case con namespacing (`project.task.created`).
- Tablas SQL: snake_case plural (`task_assignments`).

## Calidad
- Spotless para formateo y Checkstyle para reglas.
- SonarQube/SonarCloud con quality gates en CI.
- Hooks de pre-commit para formato y tests rapidos.

Para detalles y contexto, ver `arquitectura.md` seccion 11.

