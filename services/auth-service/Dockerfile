FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY build-tools/ build-tools/
COPY services/service-template/pom.xml services/service-template/
COPY services/discovery-service/pom.xml services/discovery-service/
COPY services/config-service/pom.xml services/config-service/
COPY services/api-gateway/pom.xml services/api-gateway/
COPY services/auth-service/pom.xml services/auth-service/
COPY services/user-service/pom.xml services/user-service/
COPY services/project-service/pom.xml services/project-service/
COPY services/task-service/pom.xml services/task-service/
RUN ./mvnw dependency:go-offline -pl services/auth-service -q
COPY services/auth-service/src services/auth-service/src
RUN ./mvnw package -pl services/auth-service -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/services/auth-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
