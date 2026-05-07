# ── Stage 1: build ────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copiamos el wrapper y el pom raíz primero para aprovechar la cache de Docker.
# Si solo cambia código fuente (no dependencias), esta capa no se reconstruye.
COPY mvnw ./
COPY .mvn/ .mvn/
COPY pom.xml ./
COPY services/discovery-service/pom.xml services/discovery-service/

# Descargamos dependencias del módulo en una capa separada
RUN ./mvnw dependency:go-offline -pl services/discovery-service -q

# Copiamos el código fuente y compilamos
COPY services/discovery-service/src services/discovery-service/src
RUN ./mvnw package -pl services/discovery-service -DskipTests -q

# ── Stage 2: runtime ──────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuario no-root por seguridad
RUN addgroup -S epm && adduser -S epm -G epm
USER epm

COPY --from=build /app/services/discovery-service/target/*.jar app.jar

EXPOSE 8761

ENTRYPOINT ["java", "-jar", "app.jar"]
