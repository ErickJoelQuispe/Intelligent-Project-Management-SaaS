package com.epm.ai.infrastructure.adapter.out.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.epm.ai.domain.exception.ProjectNotFoundException;
import com.epm.ai.domain.model.ProjectContext;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * WireMock integration test for {@link ProjectContextFeignAdapter}.
 *
 * <p>Stubs the project-service GET /api/v1/projects/{id} endpoint and verifies:
 * - 200 happy path: adapter maps response to {@link ProjectContext}
 * - 404 fallback: adapter throws {@link ProjectNotFoundException}
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.jwks-uri=https://example.com/.well-known/jwks.json",
    "eureka.client.enabled=false",
    "spring.ai.openai.api-key=test-key",
    "spring.ai.openai.base-url=https://api.deepseek.com"
})
class ProjectContextFeignWireMockTest {

    private static WireMockServer wireMockServer;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void configureFeign(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.project-service.url",
                () -> "http://localhost:" + wireMockServer.port());
    }

    @Autowired
    private ProjectContextFeignAdapter projectContextFeignAdapter;

    @Test
    void fetchProjectContext_happyPath_mapsResponseToProjectContext() {
        // Arrange
        String projectId = "project-123";
        String tenantId = "tenant-456";

        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/projects/" + projectId))
                .willReturn(aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withStatus(200)
                        .withBody("""
                                {
                                  "id": "project-123",
                                  "name": "Alpha Project",
                                  "description": "Build the first version",
                                  "status": "ACTIVE",
                                  "updatedAt": "2026-06-01T10:00:00Z",
                                  "memberNames": ["Alice Smith", "Bob Jones"]
                                }
                                """)));

        // Act
        ProjectContext context = projectContextFeignAdapter.fetchProjectContext(projectId, tenantId);

        // Assert
        assertThat(context.projectId()).isEqualTo("project-123");
        assertThat(context.name()).isEqualTo("Alpha Project");
        assertThat(context.description()).isEqualTo("Build the first version");
        assertThat(context.memberNames()).containsExactly("Alice Smith", "Bob Jones");
    }

    @Test
    void fetchProjectContext_404_throwsProjectNotFoundException() {
        // Arrange
        String projectId = "nonexistent-project";
        String tenantId = "tenant-456";

        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/projects/" + projectId))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"error\": \"Project not found\"}")));

        // Act + Assert
        assertThatThrownBy(() -> projectContextFeignAdapter.fetchProjectContext(projectId, tenantId))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void fetchProjectContext_noMembers_returnsEmptyMemberList() {
        // Arrange
        String projectId = "solo-project";
        String tenantId = "tenant-789";

        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/projects/" + projectId))
                .willReturn(aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withStatus(200)
                        .withBody("""
                                {
                                  "id": "solo-project",
                                  "name": "Solo Project",
                                  "description": "One-person project",
                                  "status": "ACTIVE",
                                  "updatedAt": "2026-06-01T10:00:00Z",
                                  "memberNames": null
                                }
                                """)));

        // Act
        ProjectContext context = projectContextFeignAdapter.fetchProjectContext(projectId, tenantId);

        // Assert
        assertThat(context.name()).isEqualTo("Solo Project");
        assertThat(context.memberNames()).isEmpty();
    }
}
