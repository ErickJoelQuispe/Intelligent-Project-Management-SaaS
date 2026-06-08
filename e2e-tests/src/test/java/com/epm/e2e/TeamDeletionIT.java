package com.epm.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

/**
 * E2E flow: create team → DELETE team → GET team returns 404.
 *
 * <p>Verifies the full team deletion lifecycle: a user creates a team,
 * deletes it, and confirms a subsequent GET returns 404 (team is gone).
 *
 * <p>Requires Docker stack to be running on localhost:8080 (gateway).
 */
class TeamDeletionIT extends BaseE2ETest {

    @Test
    void createTeam_returns_201_with_teamId() {
        String email = uniqueEmail();
        String token = registerAndLogin(email, "Password1!", "Team", "Creator");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"name\":\"Deletable Team\","
                      + "\"description\":\"Team to be deleted in E2E test\"}")
                .when()
                .post("/api/v1/teams")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", org.hamcrest.Matchers.equalTo("Deletable Team"));
    }

    @Test
    void deleteTeam_returns_204_and_getTeam_returns_404() {
        String email = uniqueEmail();
        String token = registerAndLogin(email, "Password1!", "Team", "Owner");

        // Create team
        String teamId = given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"name\":\"Team To Delete\","
                      + "\"description\":\"Will be deleted\"}")
                .when()
                .post("/api/v1/teams")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .extract()
                .jsonPath()
                .getString("id");

        // Delete team → 204
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/teams/" + teamId)
                .then()
                .statusCode(204);

        // GET team → 404 (team is gone)
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/teams/" + teamId)
                .then()
                .statusCode(404);
    }
}
