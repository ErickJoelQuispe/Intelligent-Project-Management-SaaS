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

RUN ./mvnw dependency:go-offline -pl services/user-service -q

COPY services/user-service/src services/user-service/src
RUN ./mvnw package -pl services/user-service -DskipTests -q

# ── Stage 2: runtime ──────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S epm && adduser -S epm -G epm
USER epm

COPY --from=build /app/services/user-service/target/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
