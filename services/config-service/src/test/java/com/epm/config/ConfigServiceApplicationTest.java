package com.epm.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    // Apunta al config-repo de test dentro del classpath — sin necesitar filesystem real
    "spring.cloud.config.server.native.search-locations=classpath:/config-repo-test",
    // Eureka deshabilitado en tests — no hay servidor corriendo
    "eureka.client.enabled=false"
})
class ConfigServiceApplicationTest {

    @Test
    void contextLoads() {
    }
}
