# ── Stage 1: build ────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY mvnw ./
COPY .mvn/ .mvn/
COPY pom.xml ./

COPY build-tools/ build-tools/
COPY services/service-template/pom.xml services/service-template/
COPY services/discovery-service/pom.xml services/discovery-service/
COPY services/config-service/pom.xml services/config-service/
COPY services/api-gateway/pom.xml services/api-gateway/
COPY services/auth-service/pom.xml services/auth-service/
COPY services/user-service/pom.xml services/user-service/
COPY services/project-service/pom.xml services/project-service/
COPY services/task-service/pom.xml services/task-service/

COPY services/notification-service/pom.xml services/notification-service/
COPY services/ai-service/pom.xml services/ai-service/
RUN ./mvnw dependency:go-offline -pl services/config-service -q

COPY services/config-service/src services/config-service/src
RUN ./mvnw package -pl services/config-service -DskipTests -q

# ── Stage 2: runtime ──────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S epm && adduser -S epm -G epm
USER epm

COPY --from=build /app/services/config-service/target/*.jar app.jar

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "app.jar"]
