# Cómo agregar un nuevo microservicio

> Seguí este checklist en orden. Cada paso depende del anterior.

---

## 1. Copiar la plantilla

```bash
cp -r services/service-template services/<nombre>
```

Renombrar el paquete Java en todos los archivos:

```bash
# Ejemplo: reemplazar "template" por "auth"
find services/<nombre>/src -type f -name "*.java" \
  -exec sed -i 's/com\.epm\.template/com.epm.<nombre>/g' {} +
find services/<nombre>/src -type f -name "*.java" \
  -exec sed -i 's/ServiceTemplate/ServiceName/g' {} +
```

Renombrar las carpetas del paquete:

```bash
mv services/<nombre>/src/main/java/com/epm/template \
   services/<nombre>/src/main/java/com/epm/<nombre>
mv services/<nombre>/src/test/java/com/epm/template \
   services/<nombre>/src/test/java/com/epm/<nombre>
```

---

## 2. Actualizar el `pom.xml` del servicio

En `services/<nombre>/pom.xml` cambiar:

```xml
<artifactId>service-template</artifactId>
<name>service-template</name>
<description>Hexagonal architecture template ...</description>
```

Por:

```xml
<artifactId><nombre>-service</artifactId>
<name><nombre>-service</name>
<description>Descripción del servicio</description>
```

Agregar las dependencias específicas que el servicio necesita (driver de Postgres, Kafka, etc.).

---

## 3. Registrar el módulo en el parent POM

En `pom.xml` raíz, agregar dentro de `<modules>`:

```xml
<module>services/<nombre>-service</module>
```

Verificar que Maven lo reconoce:

```bash
./mvnw validate --batch-mode | grep "Building"
```

---

## 4. Crear la configuración en `config-repo/`

Crear `config-repo/<nombre>-service.yml` con la config específica del servicio.
Como mínimo, la URL de su base de datos:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/<nombre>_db
```

El resto de propiedades (usuario, password, Eureka, management) las hereda de
`config-repo/application.yml`.

---

## 5. Crear la base de datos (si el servicio la necesita)

Agregar en `infra/postgres/init-databases.sh`:

```bash
CREATE DATABASE <nombre>_db;
GRANT ALL PRIVILEGES ON DATABASE <nombre>_db TO epm_admin;
```

> ⚠️ Este script solo corre cuando el volumen de Postgres es nuevo.
> Si el volumen ya existe: `docker-compose down -v && docker-compose up -d`

---

## 6. Crear la migración inicial de Flyway

En `services/<nombre>-service/src/main/resources/db/migration/` crear:

`V001__init_schema.sql` — tablas principales del servicio.

`V002__init_outbox.sql` — si el servicio publica eventos:

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

CREATE INDEX idx_outbox_unpublished
    ON outbox_events (occurred_at)
    WHERE published_at IS NULL;
```

`V003__init_processed_events.sql` — si el servicio consume eventos:

```sql
CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    consumer_group VARCHAR(100) NOT NULL
);
```

---

## 7. Agregar los topics de Kafka

En `infra/kafka/topics-init.sh` agregar los topics del nuevo bounded context:

```bash
# <nombre> context
echo "── <nombre> ──"
create_topic "<nombre>.<aggregate>.created"
create_topic "<nombre>.<aggregate>.updated"
create_topic "<nombre>.<aggregate>.created.DLT" 1
```

Aplicar los cambios:

```bash
./infra/kafka/topics-init.sh
```

---

## 8. Crear el Dockerfile

Copiar el de cualquier servicio existente y ajustar el nombre del módulo:

```dockerfile
# Stage 1: build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY mvnw ./
COPY .mvn/ .mvn/
COPY pom.xml ./
COPY services/<nombre>-service/pom.xml services/<nombre>-service/
RUN ./mvnw dependency:go-offline -pl services/<nombre>-service -q
COPY services/<nombre>-service/src services/<nombre>-service/src
RUN ./mvnw package -pl services/<nombre>-service -DskipTests -q

# Stage 2: runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S epm && adduser -S epm -G epm
USER epm
COPY --from=build /app/services/<nombre>-service/target/*.jar app.jar
EXPOSE <puerto>
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 9. Agregar el servicio al Docker Compose

En `docker-compose.yml`:

```yaml
<nombre>-service:
  build:
    context: .
    dockerfile: services/<nombre>-service/Dockerfile
  container_name: epm-<nombre>
  ports:
    - "<puerto>:<puerto>"
  depends_on:
    discovery-service:
      condition: service_healthy
    config-service:
      condition: service_healthy
    postgres:
      condition: service_healthy
  healthcheck:
    test: ["CMD-SHELL", "wget -qO- http://localhost:<puerto>/actuator/health || exit 1"]
    interval: 15s
    timeout: 10s
    retries: 5
    start_period: 40s
  restart: unless-stopped
```

---

## 10. Actualizar el workflow de CI

En `.github/workflows/ci-services.yml`, el path filter ya cubre `services/**`
por lo que el nuevo servicio queda incluido automáticamente.

Si el servicio tiene dependencias especiales (Testcontainers, bases de datos en CI),
revisar que el runner de GitHub Actions tenga acceso a Docker.

---

## 11. Verificar que todo funciona

```bash
# Tests del servicio nuevo
./mvnw test -pl services/<nombre>-service

# Build completo del reactor
./mvnw test --batch-mode

# Levantar en Docker
docker-compose up -d --build <nombre>-service
docker-compose ps

# Verificar registro en Eureka
curl http://localhost:8761/eureka/apps | grep -i <nombre>

# Verificar health via Gateway
curl http://localhost:8080/<nombre>-service/actuator/health
```

---

## Puertos reservados

| Puerto | Servicio |
|--------|----------|
| 8080 | api-gateway |
| 8761 | discovery-service |
| 8888 | config-service |
| 8081 | auth-service |
| 8082 | user-service |
| 8083 | project-service |
| 8084 | task-service |
| 8085 | ai-service |
| 8086 | notification-service |

---

## Checklist rápido

- [ ] Carpeta copiada de `service-template` y paquetes renombrados
- [ ] `pom.xml` del servicio actualizado
- [ ] Módulo registrado en `pom.xml` raíz — `./mvnw validate` pasa
- [ ] `config-repo/<nombre>-service.yml` creado con la URL de DB
- [ ] DB agregada en `init-databases.sh`
- [ ] Migraciones Flyway creadas (`V001`, `V002` outbox, `V003` processed_events)
- [ ] Topics de Kafka agregados y aplicados
- [ ] `Dockerfile` creado
- [ ] Entrada en `docker-compose.yml` con `depends_on` correcto
- [ ] `./mvnw test -pl services/<nombre>-service` pasa
- [ ] Servicio aparece en Eureka dashboard
- [ ] `curl http://localhost:8080/<nombre>-service/actuator/health` devuelve 200
