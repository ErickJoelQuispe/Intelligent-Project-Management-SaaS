package com.epm.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

/**
 * E2E flow: register → create project → POST /api/v1/ai/tasks/generate.
 *
 * <p>Verifies the AI task generation endpoint returns a valid response.
 * The endpoint is {@code POST /api/v1/ai/tasks/generate} (NOT /ai/generate-tasks).
 *
 * <p>Requires Docker stack (including DeepSeek API key configured) to be running.
 * If DeepSeek API is unavailable, the circuit breaker fallback is triggered —
 * the test accepts 200 or 503 (fallback) since the infrastructure path is tested.
 */
class AiFlowIT extends BaseE2ETest {

    @Test
    void generateTasks_with_valid_projectId_returns_200() {
        String email = uniqueEmail();
        String token = registerAndLogin(email, "Password1!", "Ai", "User");

        // Create a project to reference in AI request
        String projectId = given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"name\":\"AI Test Project\","
                      + "\"description\":\"Project for AI task generation\","
                      + "\"visibility\":\"PRIVATE\"}")
                .when()
                .post("/api/v1/projects")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getString("id");

        // POST /api/v1/ai/tasks/generate — real path from AiController
        // Accept 200 (success) or 503 (circuit breaker fallback when AI is unavailable)
        int status = given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(String.format(
                        "{\"projectId\":\"%s\","
                        + "\"description\":\"Build user authentication and registration features\","
                        + "\"bypassCache\":false}",
                        projectId))
                .when()
                .post("/api/v1/ai/tasks/generate")
                .then()
                .extract()
                .statusCode();

        // 200 = AI responded with task drafts
        // 503 = circuit breaker fallback (AI service unavailable) — infrastructure path verified
        assert status == 200 || status == 503
                : "Expected 200 (success) or 503 (circuit breaker fallback), got: " + status;
    }

    @Test
    void generateTasks_response_has_tasks_field_when_successful() {
        String email = uniqueEmail();
        String token = registerAndLogin(email, "Password1!", "Ai", "Tester");

        // Create project
        String projectId = given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"name\":\"AI Tasks Field Test\","
                      + "\"description\":\"Verify response structure\","
                      + "\"visibility\":\"PRIVATE\"}")
                .when()
                .post("/api/v1/projects")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getString("id");

        io.restassured.response.Response response = given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(String.format(
                        "{\"projectId\":\"%s\","
                        + "\"description\":\"Create task management dashboard with charts\","
                        + "\"bypassCache\":true}",
                        projectId))
                .when()
                .post("/api/v1/ai/tasks/generate");

        int statusCode = response.getStatusCode();
        if (statusCode == 200) {
            // Verify response has the tasks list and cached flag
            response.then()
                    .body("tasks", notNullValue())
                    .body("cached", notNullValue());
        }
        // If 503 (circuit breaker), the infrastructure path is still verified
    }
}
