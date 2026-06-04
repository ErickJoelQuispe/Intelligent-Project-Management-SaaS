# ADR-005: Java LTS — Mantener Java 21

## Estado
Accepted

## Fecha
2026-06-04

## Contexto

Java 21 es la versión LTS actual en producción de todos los servicios del monorepo. Con el lanzamiento de Java 24 (non-LTS, marzo 2025) y la llegada de Java 25 LTS (septiembre 2025), el equipo evaluó si era necesario actualizar la versión de la JVM base para los contenedores.

Los criterios de evaluación fueron:

1. **Compatibilidad certificada con el stack actual**: Spring Boot 3.5.x y Spring Cloud 2025.0.x.
2. **Soporte LTS a largo plazo**: el ciclo de vida de la versión elegida.
3. **Riesgo de regresión**: impacto sobre las 184+ pruebas automatizadas existentes.
4. **Madurez del ecosistema Temurin**: disponibilidad de imágenes `eclipse-temurin` estables.

## Alternativas consideradas

| Alternativa | Pros | Contras |
|-------------|------|---------|
| **Mantener Java 21 LTS** | LTS hasta septiembre 2028. Certificado en Spring Boot 3.5.x. Imágenes Temurin estables. 184 tests pasan sin cambios. | No aprovecha preview features de Java 24/25. |
| Migrar a Java 24 (non-LTS) | Features más recientes. | Soporte solo 6 meses. No es LTS. Spring Cloud 2025.0.x no oficialmente certificado en 24 al momento de la decisión. |
| Migrar a Java 25 LTS | Próximo LTS (GA sep 2025). | Al 4 jun 2026, los builds de Temurin 25 no están completamente certificados con Spring Cloud 2025.0.x. Riesgo de regresión en test suite. Fecha de revisión apropiada: Q4 2026. |

## Decisión

**Mantener `eclipse-temurin:21-jdk-alpine` (build) / `eclipse-temurin:21-jre-alpine` (runtime) en todos los servicios.**

No se realizará ningún upgrade de JVM hasta que:
- Temurin 25 tenga imágenes estables certificadas con Spring Cloud 2025.0.x, **Y**
- Se ejecute `./mvnw test` en el monorepo completo con la nueva versión sin regresiones.

## Consecuencias

**Positivas:**
- Cero riesgo de regresión: los 184+ tests existentes pasan sin modificaciones.
- Imágenes Temurin 21 son estables, reproducibles y aceptadas en pipelines de producción.
- Spring Boot 3.5.x y Spring Cloud 2025.0.x están completamente certificados en Java 21.
- Soporte LTS garantizado hasta septiembre 2028 — ventana de 2+ años.
- ArchUnit 1.4.1 (ya presente en todos los servicios) es compatible con Java 21.

**Negativas:**
- No se aprovechan las mejoras de rendimiento de Java 24+ (mejoras de GC, Loom refinements).
- Preview features de versiones posteriores no disponibles.

**Mitigaciones:**
- Fecha de revisión fijada: **Q4 2026**. Evaluar Temurin 25 cuando Spring Cloud 2025.0.x lo certifique oficialmente.
- El script `scripts/sync-dockerfiles.sh` facilita la actualización coordinada de todos los Dockerfiles cuando se decida migrar.

## Referencias

- [Spring Boot 3.5.x System Requirements](https://docs.spring.io/spring-boot/docs/3.5.x/reference/html/getting-started.html#getting-started.system-requirements)
- [Eclipse Temurin releases](https://adoptium.net/temurin/releases/)
- [Java 21 LTS roadmap — Adoptium](https://adoptium.net/support/)
- Design decision D2 en `sdd/phase-5/design` (Engram id: 654)
