package com.epm.task.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Level-2 binding test: verifies that the Feign per-client timeout config in
 * the PRODUCTION {@code application.yml} is bound to the correct Spring Cloud 2025.x
 * namespace ({@code spring.cloud.openfeign.client.config.project-service}).
 *
 * <p>Approach: pure {@link Binder} — no Spring context, no Postgres, no Kafka.
 * Loads {@code src/main/resources/application.yml} directly via {@link FileSystemResource}
 * so the test classpath's {@code application.yml} (which overrides it) does not
 * shadow the production config. Runs in milliseconds under Surefire.
 *
 * <p>Regression guard: this test would have caught the H1 bug where config lived
 * under the dead legacy namespace {@code feign.client.config.projectService}
 * (Spring Cloud Netflix, no longer bound in Spring Cloud 2025.x).
 *
 * <p>Why FileSystemResource: the test classpath has its own {@code application.yml}
 * that does NOT include the Feign section (it is a minimal test override). A plain
 * {@code ClassPathResource("application.yml")} resolves the test one first, leaving
 * the Binder with no {@code spring.cloud.openfeign.client} entry to bind.
 * Maven always executes tests from the module root, so the relative path
 * {@code src/main/resources/application.yml} is stable and portable.
 */
class FeignTimeoutBindingTest {

    private static final String CLIENT_NAME = "project-service";
    private static final int EXPECTED_CONNECT_TIMEOUT_MS = 2000;
    private static final int EXPECTED_READ_TIMEOUT_MS    = 2000;

    @Test
    void projectServiceFeignClient_hasCorrectTimeoutsInApplicationYml() throws Exception {
        // --- Load the PRODUCTION application.yml (not the test-classpath override) ---
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load(
                "production-application-yml",
                new FileSystemResource(
                        Paths.get("src", "main", "resources", "application.yml").toFile()
                )
        );

        assertThat(sources)
                .as("application.yml must be loadable from src/main/resources")
                .isNotEmpty();

        // Populate a StandardEnvironment with the loaded YAML documents
        StandardEnvironment environment = new StandardEnvironment();
        // Add in reverse order so the first YAML document wins (highest priority first)
        for (int i = sources.size() - 1; i >= 0; i--) {
            environment.getPropertySources().addFirst(sources.get(i));
        }

        // --- Bind spring.cloud.openfeign.client → FeignClientProperties ---
        FeignClientProperties props = Binder.get(environment)
                .bind("spring.cloud.openfeign.client", FeignClientProperties.class)
                .orElseThrow(() -> new AssertionError(
                        "No properties bound under 'spring.cloud.openfeign.client'. " +
                        "Check that application.yml uses the correct namespace " +
                        "(spring.cloud.openfeign.client.config.<client-name>) " +
                        "and NOT the legacy 'feign.client.config' namespace."
                ));

        // --- Assert the project-service entry is present and has correct values ---
        assertThat(props.getConfig())
                .as("spring.cloud.openfeign.client.config must contain a '%s' entry — " +
                    "the key must match @FeignClient(name = \"%s\")", CLIENT_NAME, CLIENT_NAME)
                .containsKey(CLIENT_NAME);

        FeignClientProperties.FeignClientConfiguration cfg = props.getConfig().get(CLIENT_NAME);

        assertThat(cfg.getConnectTimeout())
                .as("connectTimeout for '%s' must be %d ms", CLIENT_NAME, EXPECTED_CONNECT_TIMEOUT_MS)
                .isEqualTo(EXPECTED_CONNECT_TIMEOUT_MS);

        assertThat(cfg.getReadTimeout())
                .as("readTimeout for '%s' must be %d ms", CLIENT_NAME, EXPECTED_READ_TIMEOUT_MS)
                .isEqualTo(EXPECTED_READ_TIMEOUT_MS);
    }
}
