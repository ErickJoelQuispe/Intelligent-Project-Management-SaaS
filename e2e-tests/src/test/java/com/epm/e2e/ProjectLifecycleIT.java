package com.epm.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

/**
 * E2E flow: create project → assign team → create task.
 *
 * <p>Verifies the complete project lifecycle: a user registers, creates a project,
 * assigns a team to it, and creates a task within that project.
 *
 * <p>Requires Docker stack to be running on localhost:8080 (gateway).
 */
class ProjectLifecycleIT extends BaseE2ETest {

    @Test
    void createProject_returns_201_with_projectId() {
        String email = uniqueEmail();
        String token = registerAndLogin(email, "Password1!", "Project", "Owner");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"name\":\"E2E Test Project\","
                      + "\"description\":\"Created by E2E test\","
                      + "\"visibility\":\"PRIVATE\"}")
                .when()
                .post("/api/v1/projects")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", org.hamcrest.Matchers.equalTo("E2E Test Project"));
    }

    @Test
    void createTeam_and_assignToProject_returns_201() {
        String email = uniqueEmail();
        String token = registerAndLogin(email, "Password1!", "Team", "Owner");

        // Create team
        String teamId = given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"name\":\"E2E Team\",\"description\":\"Team for E2E test\"}")
                .when()
                .post("/api/v1/teams")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .extract()
                .jsonPath()
                .getString("id");

        // Create project
        String projectId = given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"name\":\"E2E Project With Team\","
                      + "\"description\":\"Project to assign team\","
                      + "\"visibility\":\"PRIVATE\"}")
                .when()
                .post("/api/v1/projects")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getString("id");

        // Assign team to project
        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(String.format("{\"teamId\":\"%s\"}", teamId))
                .when()
                .post("/api/v1/projects/" + projectId + "/teams")
                .then()
                .statusCode(201);
    }

    @Test
    void createTask_in_project_returns_201_with_taskId() {
        String email = uniqueEmail();
        String token = registerAndLogin(email, "Password1!", "Task", "Creator");

        // Create project
        String projectId = given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"name\":\"E2E Task Project\","
                      + "\"description\":\"Project for task creation\","
                      + "\"visibility\":\"PRIVATE\"}")
                .when()
                .post("/api/v1/projects")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getString("id");

        // Create task in project
        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(String.format(
                        "{\"projectId\":\"%s\","
                        + "\"title\":\"E2E Created Task\","
                        + "\"description\":\"Task from E2E test\","
                        + "\"priority\":\"MEDIUM\"}",
                        projectId))
                .when()
                .post("/api/v1/tasks")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("title", org.hamcrest.Matchers.equalTo("E2E Created Task"))
                .body("projectId", org.hamcrest.Matchers.equalTo(projectId));
    }
}
