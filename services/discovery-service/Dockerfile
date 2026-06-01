# ── Stage 1: build ────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY mvnw ./
COPY .mvn/ .mvn/
COPY pom.xml ./

# El parent POM declara todos los módulos — Maven necesita encontrar
# el pom.xml de cada uno para resolver el reactor, aunque no los compile.
COPY build-tools/ build-tools/
COPY services/service-template/pom.xml services/service-template/
COPY services/discovery-service/pom.xml services/discovery-service/
COPY services/config-service/pom.xml services/config-service/
COPY services/api-gateway/pom.xml services/api-gateway/
COPY services/auth-service/pom.xml services/auth-service/
COPY services/user-service/pom.xml services/user-service/

# Descarga dependencias solo del módulo a compilar
RUN ./mvnw dependency:go-offline -pl services/discovery-service -q

# Copia el código fuente y compila
COPY services/discovery-service/src services/discovery-service/src
RUN ./mvnw package -pl services/discovery-service -DskipTests -q

# ── Stage 2: runtime ──────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S epm && adduser -S epm -G epm
USER epm

COPY --from=build /app/services/discovery-service/target/*.jar app.jar

EXPOSE 8761

ENTRYPOINT ["java", "-jar", "app.jar"]
