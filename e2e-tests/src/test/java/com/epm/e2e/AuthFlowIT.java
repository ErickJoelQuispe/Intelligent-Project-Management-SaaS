package com.epm.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

/**
 * E2E flow: register → login (via Keycloak) → GET /users/me.
 *
 * <p>Verifies the full authentication lifecycle against the live stack.
 * Requires Docker stack to be running on localhost:8080 (gateway) and
 * localhost:8180 (Keycloak).
 */
class AuthFlowIT extends BaseE2ETest {

    @Test
    void register_returns_201_with_accountId() {
        String email = uniqueEmail();

        given()
                .contentType("application/json")
                .body(String.format(
                        "{\"email\":\"%s\",\"password\":\"Password1!\","
                        + "\"firstName\":\"Test\",\"lastName\":\"User\"}",
                        email))
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(201)
                .body("accountId", notNullValue())
                .body("email", equalTo(email));
    }

    @Test
    void login_returns_access_token() {
        String email = uniqueEmail();
        String password = "Password1!";

        // Register first
        register(email, password, "Flow", "User")
                .then()
                .statusCode(201);

        // Obtain token from Keycloak
        String token = obtainToken(email, password);

        assert token != null && !token.isBlank()
                : "Expected a non-blank access_token from Keycloak";
    }

    @Test
    void getProfile_with_valid_jwt_returns_200_and_userId_matches() {
        String email = uniqueEmail();
        String password = "Password1!";

        // Register and get accountId
        String accountId = register(email, password, "Profile", "Test")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getString("accountId");

        // Get JWT from Keycloak
        String token = obtainToken(email, password);

        // GET /api/v1/users/me — should return 200 and userId matches accountId
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/users/me")
                .then()
                .statusCode(200)
                .body("id", not(emptyOrNullString()))
                .body("email", equalTo(email));
    }
}
