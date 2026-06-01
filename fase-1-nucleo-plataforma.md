# Fase 1 — Núcleo de plataforma: guía paso a paso

> **Prerequisito:** la Fase 0 está completa. `docker-compose up -d` levanta toda la infra sin errores y todos los contenedores están `healthy`.
>
> **Responsabilidad de esta fase:** implementar los tres servicios de infraestructura que el resto del sistema necesita para existir. Sin estos, ningún microservicio de dominio puede registrarse, obtener su configuración ni recibir tráfico externo.

---

## ¿Qué estás construyendo en esta fase?

```
                    ┌─────────────────────────────────┐
                    │   Frontend / cliente externo     │
                    └────────────────┬────────────────┘
                                     │ HTTPS
                                     ▼
                    ┌─────────────────────────────────┐
                    │   API Gateway  :8080             │  ← vos lo construís
                    │   routing, JWT, CORS, traceId    │
                    └───┬─────────────────────────────┘
                        │ descubre servicios via Eureka
                        ▼
                    ┌─────────────────────────────────┐
                    │   Discovery Service  :8761       │  ← vos lo construís
                    │   (Eureka Server)                │
                    └─────────────────────────────────┘

                    ┌─────────────────────────────────┐
                    │   Config Service  :8888          │  ← vos lo construís
                    │   configuración centralizada     │
                    └─────────────────────────────────┘
```

Al terminar esta fase, un cliente externo puede enviar un request con un JWT válido al Gateway, el Gateway lo valida contra Keycloak, y lo enruta al servicio correcto. Los servicios de dominio todavía no existen — pero la autopista está construida.

**Criterio de salida:** `curl -H "Authorization: Bearer <token>" http://localhost:8080/actuator/health` devuelve 200.

---

## Estructura del documento

Cada paso tiene:
- 🎯 **Objetivo** — qué logra este paso
- 📚 **Concepto clave** — por qué importa
- ✅ **Entregable concreto** — cómo sabés que está hecho
- ⚠️ **Errores comunes** — lo que suele salir mal

---

## Paso 1 — Registrar los módulos en el parent POM

### 🎯 Objetivo
Que Maven conozca los tres servicios nuevos como módulos del monorepo.

### 📚 Concepto clave: reactor build de Maven
Con módulos declarados en el parent POM, `mvn test` desde la raíz corre los tests de TODOS los servicios en orden de dependencia. El reactor resuelve el orden automáticamente. Sin esto, tenés que entrar a cada carpeta y correr Maven por separado — inmanejable a medida que crece el sistema.

### Tareas

1. **Agregar los tres módulos al `pom.xml` raíz:**

```xml
<modules>
    <module>services/service-template</module>
    <module>services/discovery-service</module>
    <module>services/config-service</module>
    <module>services/api-gateway</module>
</modules>
```

2. **Crear las carpetas** (ya existen con `.gitkeep` de la Fase 0):
```bash
# Ya existen, solo verificar
ls services/discovery-service
ls services/config-service
ls services/api-gateway
```

3. **Eliminar los `.gitkeep`** de esas tres carpetas — van a tener contenido real ahora.

### ✅ Entregable
- `pom.xml` raíz con los 4 módulos declarados.
- `mvn validate` desde la raíz pasa (aunque los módulos nuevos estén vacíos todavía).

---

## Paso 2 — Discovery Service (Eureka Server)

### 🎯 Objetivo
Un servidor Eureka corriendo en `:8761` donde todos los microservicios se registran al arrancar.

### 📚 Concepto clave: service discovery
En un sistema distribuido, los servicios escalan horizontalmente — podés tener 3 instancias de `task-service` corriendo en distintos puertos. ¿Cómo sabe el Gateway a cuál mandarle el tráfico? No podés hardcodear IPs.

Eureka resuelve esto con un registro dinámico: cada servicio al arrancar dice "hola, soy `task-service` y estoy en `10.0.0.5:8083`". El Gateway consulta Eureka: "¿quién puede atender `task-service`?" y obtiene la lista actualizada. Si una instancia cae, Eureka la elimina del registro automáticamente tras unos segundos sin heartbeat.

### Tareas

1. **Crear `services/discovery-service/pom.xml`:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.epm</groupId>
        <artifactId>epm-parent</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>discovery-service</artifactId>
    <packaging>jar</packaging>
    <name>discovery-service</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

2. **Crear la clase principal** `src/main/java/com/epm/discovery/DiscoveryServiceApplication.java`:

```java
package com.epm.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServiceApplication.class, args);
    }
}
```

> **Nota:** `@EnableEurekaServer` es la única anotación que lo diferencia de cualquier otra app Spring Boot. Todo el servidor Eureka está dentro del autoconfigure de Spring Cloud.

3. **Crear `src/main/resources/application.yml`:**

```yaml
spring:
  application:
    name: discovery-service

server:
  port: 8761

eureka:
  instance:
    hostname: localhost
  client:
    # Eureka no se registra a sí mismo
    register-with-eureka: false
    fetch-registry: false
    service-url:
      defaultZone: http://${eureka.instance.hostname}:${server.port}/eureka/
  server:
    # Deshabilitar la protección de auto-preservación en desarrollo.
    # En producción esto va en true — protege el registro cuando hay
    # particiones de red. En dev genera falsos positivos cuando paramos servicios.
    enable-self-preservation: false
    eviction-interval-timer-in-ms: 5000

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
```

4. **Crear `src/test/java/com/epm/discovery/DiscoveryServiceApplicationTest.java`:**

```java
package com.epm.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DiscoveryServiceApplicationTest {

    @Test
    void contextLoads() {
    }
}
```

5. **Verificar que compila y los tests pasan:**
```bash
./mvnw test -pl services/discovery-service
```

6. **Levantar y verificar el dashboard:**
```bash
./mvnw spring-boot:run -pl services/discovery-service
```
Abrí `http://localhost:8761` — vas a ver el dashboard de Eureka con "No instances available".

### ✅ Entregable
- Dashboard Eureka accesible en `http://localhost:8761`.
- `mvn test` pasa.
- No hay instancias registradas todavía — eso es correcto.

### ⚠️ Errores comunes
- Olvidar `register-with-eureka: false` — Eureka intenta registrarse a sí mismo y entra en un loop de errores en los logs.
- Confundir el puerto: Eureka usa `8761` por convención. El Gateway usa `8080`. No los mezcles.

---

## Paso 3 — Config Service

### 🎯 Objetivo
Un servidor de configuración centralizada en `:8888` que sirve los `application.yml` de cada servicio desde `config-repo/`.

### 📚 Concepto clave: configuración centralizada
Cada microservicio tiene propiedades que cambian por entorno: URLs de base de datos, credenciales, feature flags. Sin Config Server, cada servicio tiene su propio `application.yml` hardcodeado. Para cambiar la URL de Redis en 9 servicios, tocás 9 archivos.

Con Config Server, hay UN solo lugar donde viven todas las configuraciones. Los servicios al arrancar preguntan: "dame mi configuración para el perfil `dev`". Esto también permite cambiar configuración en caliente (`@RefreshScope`) sin reiniciar servicios.

En desarrollo usamos `config-repo/` como carpeta local. En producción esa carpeta es un repositorio Git separado con historial, branches por entorno y cifrado de secrets.

### Tareas

1. **Crear `services/config-service/pom.xml`:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.epm</groupId>
        <artifactId>epm-parent</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>config-service</artifactId>
    <packaging>jar</packaging>
    <name>config-service</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-config-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

2. **Crear la clase principal** `src/main/java/com/epm/config/ConfigServiceApplication.java`:

```java
package com.epm.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class ConfigServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServiceApplication.class, args);
    }
}
```

3. **Crear `src/main/resources/application.yml`:**

```yaml
spring:
  application:
    name: config-service
  cloud:
    config:
      server:
        native:
          # En desarrollo leemos configuración desde el sistema de archivos local.
          # La ruta es relativa a donde se lanza el proceso.
          # En producción esto apunta a un repositorio Git.
          search-locations: file:${user.dir}/config-repo
  profiles:
    active: native   # "native" = filesystem; alternativa: "git"

server:
  port: 8888

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,refresh
  endpoint:
    health:
      show-details: always
```

4. **Crear las configuraciones compartidas en `config-repo/`.**

Primero el archivo base que aplica a TODOS los servicios:

`config-repo/application.yml`:
```yaml
# Configuración compartida por todos los microservicios.
# Cada servicio puede sobreescribir cualquier valor en su propio archivo.

spring:
  datasource:
    username: epm_admin
    password: changeme
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always

logging:
  level:
    com.epm: DEBUG
```

Luego uno por servicio (placeholders por ahora — se completan en cada fase):

`config-repo/discovery-service.yml`:
```yaml
# Configuración específica del discovery-service.
# Por ahora vacío — usa solo los defaults de application.yml
```

`config-repo/config-service.yml`:
```yaml
# Configuración específica del config-service.
```

`config-repo/api-gateway.yml`:
```yaml
# Configuración específica del api-gateway.
# Se completa en el Paso 4.
```

5. **Verificar que el Config Server sirve configuración:**
```bash
./mvnw spring-boot:run -pl services/config-service
# En otra terminal:
curl http://localhost:8888/application/default
```
Deberías ver el JSON con la configuración de `application.yml`.

6. **Tests:**

`src/test/java/com/epm/config/ConfigServiceApplicationTest.java`:
```java
package com.epm.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.cloud.config.server.native.search-locations=classpath:/config-repo-test",
    "eureka.client.enabled=false"
})
class ConfigServiceApplicationTest {

    @Test
    void contextLoads() {
    }
}
```

Crear `src/test/resources/config-repo-test/application.yml` vacío para que el test no falle buscando la carpeta:
```yaml
# test config repo placeholder
```

### ✅ Entregable
- `curl http://localhost:8888/application/default` devuelve la configuración en JSON.
- `curl http://localhost:8888/api-gateway/default` devuelve la configuración del gateway.
- `mvn test` pasa.

### ⚠️ Errores comunes
- La ruta `file:${user.dir}/config-repo` es relativa al directorio desde donde lanzás Maven. Si lo lanzás desde `services/config-service/` en lugar de la raíz, no encuentra los archivos. Siempre lanzá desde la raíz del proyecto.
- Olvidar el perfil `native` — sin él, Spring Cloud Config intenta conectarse a un repositorio Git que no existe y falla al arrancar.

---

## Paso 4 — API Gateway

### 🎯 Objetivo
El único punto de entrada al sistema: valida JWTs contra Keycloak, enruta al servicio correcto vía Eureka, agrega CORS y propaga un `traceId` en cada request.

### 📚 Concepto clave: API Gateway Pattern
El Gateway es el portero del edificio. Ningún cliente externo conoce las IPs ni puertos de los microservicios — solo conocen el Gateway. Esto da:

- **Seguridad perimetral:** validación de JWT en un solo lugar. Los microservicios confían en que si el request llegó del Gateway, ya está autenticado.
- **Routing dinámico:** el Gateway consulta Eureka y balancea carga automáticamente entre instancias.
- **Observabilidad:** un solo lugar para loggear todos los requests entrantes con su `traceId`.
- **CORS centralizado:** no configurás CORS en cada servicio.

Spring Cloud Gateway está construido sobre Reactor (programación reactiva). Esto importa porque el Gateway NO debe tener lógica de negocio — solo ruteo, filtros y seguridad perimetral.

### Tareas

1. **Crear `services/api-gateway/pom.xml`:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.epm</groupId>
        <artifactId>epm-parent</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>api-gateway</artifactId>
    <packaging>jar</packaging>
    <name>api-gateway</name>

    <dependencies>
        <!-- Gateway reactivo -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>

        <!-- Descubrimiento de servicios -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>

        <!-- Configuración centralizada -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-config</artifactId>
        </dependency>

        <!-- Seguridad reactiva + OAuth2 Resource Server -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
        </dependency>

        <!-- Actuator -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

2. **Crear la clase principal** `src/main/java/com/epm/gateway/ApiGatewayApplication.java`:

```java
package com.epm.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

3. **Crear la configuración de seguridad** `src/main/java/com/epm/gateway/infrastructure/security/SecurityConfig.java`:

```java
package com.epm.gateway.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Configura el Gateway como OAuth2 Resource Server.
 *
 * El Gateway valida el JWT contra el JWKS de Keycloak (cacheado localmente).
 * Si el token es válido, el request se enruta al microservicio correspondiente.
 * Si no, devuelve 401 directamente — el microservicio nunca ve el request.
 */
@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                // Actuator y health son públicos
                .pathMatchers("/actuator/**").permitAll()
                // Todo lo demás requiere JWT válido
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> {})
            );
        return http.build();
    }
}
```

4. **Crear el filtro de traceId** `src/main/java/com/epm/gateway/infrastructure/filter/TraceIdFilter.java`:

```java
package com.epm.gateway.infrastructure.filter;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Filtro global: asegura que cada request tenga un X-Request-ID único.
 *
 * Si el cliente envía X-Request-ID, se respeta.
 * Si no, se genera un UUID nuevo.
 * El header se propaga al microservicio de destino y se incluye en la respuesta.
 * Esto permite correlacionar logs entre Gateway y microservicios.
 */
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);
    private static final String TRACE_ID_HEADER = "X-Request-ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        final String finalTraceId = traceId;
        log.debug("Request: method={} path={} traceId={}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath(),
                finalTraceId);

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(TRACE_ID_HEADER, finalTraceId)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .then(Mono.fromRunnable(() ->
                        exchange.getResponse().getHeaders().add(TRACE_ID_HEADER, finalTraceId)));
    }

    @Override
    public int getOrder() {
        // Ejecutar antes que cualquier otro filtro
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
```

5. **Crear `src/main/resources/application.yml`:**

```yaml
spring:
  application:
    name: api-gateway
  config:
    import: "optional:configserver:http://localhost:8888"
  cloud:
    gateway:
      # Habilita routing automático desde Eureka.
      # Un request a /auth-service/** se enruta automáticamente
      # al servicio registrado como AUTH-SERVICE en Eureka.
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
      # CORS centralizado para el frontend Angular
      globalcors:
        cors-configurations:
          '[/**]':
            allowed-origins:
              - "http://localhost:4200"
            allowed-methods:
              - GET
              - POST
              - PUT
              - PATCH
              - DELETE
              - OPTIONS
            allowed-headers: "*"
            allow-credentials: true
            max-age: 3600

server:
  port: 8080

# Keycloak JWKS endpoint — el Gateway descarga las claves públicas
# para verificar JWTs localmente sin llamar a Keycloak en cada request.
spring.security.oauth2.resourceserver.jwt.jwks-uri: >
  http://localhost:8180/realms/epm/protocol/openid-connect/certs

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,gateway
  endpoint:
    health:
      show-details: always

logging:
  level:
    com.epm: DEBUG
    org.springframework.cloud.gateway: DEBUG
```

> **Por qué JWKS y no introspección:** el Gateway descarga las claves públicas de Keycloak UNA vez y las cachea. Cada validación de JWT es local — no hay llamada HTTP a Keycloak por request. Esto es crucial para el rendimiento: un sistema con 1000 req/s no puede hacer 1000 llamadas/s a Keycloak.

6. **Crear `src/test/java/com/epm/gateway/ApiGatewayApplicationTest.java`:**

```java
package com.epm.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.config.import=optional:configserver:",
    "eureka.client.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.jwks-uri=https://example.com/.well-known/jwks.json"
})
class ApiGatewayApplicationTest {

    @Test
    void contextLoads() {
    }
}
```

7. **Actualizar `config-repo/api-gateway.yml`** con la configuración definitiva (igual al `application.yml` de arriba — el Config Server lo sirve desde ahí en producción).

### ✅ Entregable
- `http://localhost:8080/actuator/health` devuelve `{"status":"UP"}`.
- Un request sin token a cualquier otra ruta devuelve `401`.
- El dashboard de Eureka en `http://localhost:8761` muestra `API-GATEWAY` registrado.
- `mvn test` pasa.

### ⚠️ Errores comunes
- Usar `spring-boot-starter-web` en lugar de dejar Spring Cloud Gateway usar WebFlux. El Gateway es **reactivo** — si agregás el starter MVC hay un conflicto de contexto que impide que arranque.
- Configurar el `jwks-uri` apuntando a `localhost` en los tests. El contexto de test falla al intentar conectarse a Keycloak. Sobreescribilo con `@TestPropertySource` como en el ejemplo.
- Olvidar `lower-case-service-id: true` en el discovery locator. Sin esto, el Gateway enruta a `AUTH-SERVICE` (mayúsculas) pero los paths esperan `auth-service` (minúsculas) y los requests no matchean.

---

## Paso 5 — Agregar ADR-004

### 🎯 Objetivo
Documentar la decisión de usar Spring Cloud Gateway sobre alternativas como Kong o nginx.

### Tareas

Crear `docs/adr/ADR-004-api-gateway.md` con:
- **Contexto:** necesidad de un punto único de entrada con validación JWT, routing dinámico y CORS.
- **Alternativas:** Kong (más features, más complejo), nginx (no integra con Eureka/Spring Security nativamente), AWS API Gateway (lock-in en cloud).
- **Decisión:** Spring Cloud Gateway — integración nativa con Eureka, Spring Security y el ecosistema Spring. WebFlux reactivo — maneja alta concurrencia con pocos threads.
- **Consecuencias:** el Gateway es el único componente reactivo del sistema (los microservicios de dominio usan MVC bloqueante con virtual threads de Java 21).

---

## Paso 6 — Dockerfiles y Docker Compose override

### 🎯 Objetivo
Cada servicio tiene su `Dockerfile` y el `docker-compose.yml` puede levantar los tres servicios infra junto con Postgres, Kafka, Keycloak y Redis.

### 📚 Concepto clave: Dockerfile multi-stage
Un Dockerfile multi-stage tiene dos fases:
1. **Build stage:** imagen con JDK completo que compila el código.
2. **Runtime stage:** imagen mínima con solo JRE que corre el JAR.

El resultado es una imagen final de ~180MB en lugar de ~600MB. La imagen de build nunca llega a producción.

### Tareas

1. **Crear `services/discovery-service/Dockerfile`:**

```dockerfile
# ── Stage 1: build ────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY services/discovery-service/pom.xml services/discovery-service/
RUN ./mvnw dependency:go-offline -pl services/discovery-service -q
COPY services/discovery-service/src services/discovery-service/src
RUN ./mvnw package -pl services/discovery-service -DskipTests -q

# ── Stage 2: runtime ──────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/services/discovery-service/target/*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
```

2. **Crear el mismo patrón** para `config-service` (puerto 8888) y `api-gateway` (puerto 8080).

3. **Agregar los servicios al `docker-compose.yml`:**

```yaml
  discovery-service:
    build:
      context: .
      dockerfile: services/discovery-service/Dockerfile
    container_name: epm-discovery
    ports:
      - "8761:8761"
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8761/actuator/health || exit 1"]
      interval: 15s
      timeout: 10s
      retries: 5
      start_period: 30s
    restart: unless-stopped

  config-service:
    build:
      context: .
      dockerfile: services/config-service/Dockerfile
    container_name: epm-config
    ports:
      - "8888:8888"
    volumes:
      - ./config-repo:/app/config-repo
    depends_on:
      discovery-service:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8888/actuator/health || exit 1"]
      interval: 15s
      timeout: 10s
      retries: 5
      start_period: 30s
    restart: unless-stopped

  api-gateway:
    build:
      context: .
      dockerfile: services/api-gateway/Dockerfile
    container_name: epm-gateway
    ports:
      - "8080:8080"
    depends_on:
      discovery-service:
        condition: service_healthy
      config-service:
        condition: service_healthy
      keycloak:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health || exit 1"]
      interval: 15s
      timeout: 10s
      retries: 5
      start_period: 40s
    restart: unless-stopped
```

> **Nota sobre `depends_on`:** garantiza el orden de arranque. El Gateway no arranca hasta que Eureka y Config estén healthy. Config no arranca hasta que Eureka esté healthy.

### ✅ Entregable
- `docker-compose up -d` levanta los 7 contenedores sin errores.
- `docker-compose ps` muestra todos `healthy`.

---

## Paso 7 — Documentar cómo agregar un nuevo servicio

### 🎯 Objetivo
Que el procedimiento de agregar un microservicio al sistema quede documentado y sea reproducible.

### Tareas

Crear `docs/how-to-add-service.md` con el checklist exacto:

```markdown
# Cómo agregar un nuevo microservicio

## Checklist

1. Copiar `services/service-template/` → `services/<nombre>/`
2. Renombrar el paquete `com.epm.template` → `com.epm.<nombre>`
3. Agregar el módulo en `pom.xml` raíz
4. Crear `config-repo/<nombre>.yml` con la configuración específica
5. Agregar la DB en `infra/postgres/init-databases.sh` (si necesita persistencia)
6. Agregar el servicio en `docker-compose.yml` con `depends_on` a Eureka y Config
7. Crear el `Dockerfile` multi-stage
8. Actualizar el `README.md` con la URL del nuevo servicio
9. Crear el topic de Kafka correspondiente en `infra/kafka/topics-init.sh`
10. Agregar el path al workflow de CI en `.github/workflows/ci-services.yml`
```

---

## Checklist final de Fase 1

### discovery-service
- [ ] `pom.xml` con `spring-cloud-starter-netflix-eureka-server`
- [ ] `@EnableEurekaServer` en la clase principal
- [ ] `register-with-eureka: false` y `fetch-registry: false`
- [ ] Dashboard accesible en `http://localhost:8761`
- [ ] `mvn test` pasa

### config-service
- [ ] `pom.xml` con `spring-cloud-config-server`
- [ ] `@EnableConfigServer` en la clase principal
- [ ] Perfil `native` apuntando a `config-repo/`
- [ ] `config-repo/application.yml` con configuración compartida
- [ ] `curl http://localhost:8888/application/default` devuelve JSON
- [ ] `mvn test` pasa

### api-gateway
- [ ] `pom.xml` con Gateway + Security + OAuth2 Resource Server
- [ ] `SecurityConfig` con JWT validation
- [ ] `TraceIdFilter` propagando `X-Request-ID`
- [ ] Discovery locator habilitado con `lower-case-service-id: true`
- [ ] CORS configurado para `localhost:4200`
- [ ] `jwks-uri` apuntando a Keycloak realm `epm`
- [ ] `http://localhost:8080/actuator/health` devuelve `{"status":"UP"}`
- [ ] Request sin token devuelve `401`
- [ ] Gateway aparece en Eureka dashboard
- [ ] `mvn test` pasa

### Infraestructura
- [ ] Dockerfiles multi-stage para los 3 servicios
- [ ] `docker-compose.yml` actualizado con los 3 servicios
- [ ] `depends_on` con `condition: service_healthy` configurado
- [ ] `docker-compose up -d` levanta los 7 contenedores healthy

### Documentación
- [ ] `docs/adr/ADR-004-api-gateway.md` creado
- [ ] `docs/how-to-add-service.md` creado

---

## ¿Qué sigue?

Con la Fase 1 completa tenés la autopista lista. La **Fase 2** construye los primeros servicios de dominio que van a circular por ella: `auth-service` y `user-service`. Un usuario va a poder registrarse, loguearse y obtener un JWT válido que el Gateway acepta.

> **Recordá:** no avancés a Fase 2 con items pendientes de Fase 1. El Gateway validando JWTs es el prerequisito de todo lo que viene después.
