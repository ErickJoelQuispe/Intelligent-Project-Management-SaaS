package com.epm.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.config.import=optional:configserver:",
    "eureka.client.enabled=false",
    // jwks-uri debe estar presente para que el autoconfigure no falle,
    // pero el ReactiveJwtDecoder real se reemplaza por un mock abajo.
    "spring.security.oauth2.resourceserver.jwt.jwks-uri=https://example.com/.well-known/jwks.json"
})
class ApiGatewayApplicationTest {

    // Reemplaza el decoder real (que intentaría conectarse a Keycloak)
    // por un mock vacío. El contexto levanta sin red externa.
    @MockitoBean
    ReactiveJwtDecoder reactiveJwtDecoder;

    @Test
    void contextLoads() {
    }
}
