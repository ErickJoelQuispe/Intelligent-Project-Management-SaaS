package com.epm.e2e;

import static io.restassured.RestAssured.given;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;

/**
 * Base class for all E2E integration tests.
 *
 * <p>Sets up RestAssured base URI from system property {@code gateway.url}
 * (defaults to {@code http://localhost:8080}).
 *
 * <p>Login is handled via Keycloak's token endpoint directly — the auth-service
 * only provides register/logout; there is no /auth/login endpoint.
 */
@Tag("e2e")
public abstract class BaseE2ETest {

    protected static final String GATEWAY_URL =
            System.getProperty("gateway.url", "http://localhost:8080");

    protected static final String KEYCLOAK_URL =
            System.getProperty("keycloak.url", "http://localhost:8180");

    protected static final String KEYCLOAK_REALM =
            System.getProperty("keycloak.realm", "epm");

    protected static final String KEYCLOAK_CLIENT_ID =
            System.getProperty("keycloak.clientId", "epm-frontend");

    @BeforeAll
    static void setUpRestAssured() {
        RestAssured.baseURI = GATEWAY_URL;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    /**
     * Registers a new account via POST /api/v1/auth/register.
     *
     * @param email     unique email address
     * @param password  password (min 8 chars)
     * @param firstName first name
     * @param lastName  last name
     * @return HTTP response (expect 201 on success)
     */
    protected Response register(String email, String password,
                                String firstName, String lastName) {
        String body = String.format(
                "{\"email\":\"%s\",\"password\":\"%s\","
                + "\"firstName\":\"%s\",\"lastName\":\"%s\"}",
                email, password, firstName, lastName);
        return given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/v1/auth/register");
    }

    /**
     * Obtains a JWT access token from Keycloak using Resource Owner Password Credentials grant.
     *
     * @param email    the user's email (used as username in Keycloak)
     * @param password the user's password
     * @return the JWT access token string
     */
    protected String obtainToken(String email, String password) {
        String tokenEndpoint = KEYCLOAK_URL + "/realms/" + KEYCLOAK_REALM
                + "/protocol/openid-connect/token";
        return given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("grant_type", "password")
                .formParam("client_id", KEYCLOAK_CLIENT_ID)
                .formParam("username", email)
                .formParam("password", password)
                .when()
                .post(tokenEndpoint)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("access_token");
    }

    /**
     * Registers a new account and then obtains a JWT token for it.
     *
     * @param email     unique email address
     * @param password  password
     * @param firstName first name
     * @param lastName  last name
     * @return the JWT access token for the newly registered user
     */
    protected String registerAndLogin(String email, String password,
                                      String firstName, String lastName) {
        register(email, password, firstName, lastName)
                .then()
                .statusCode(201);
        return obtainToken(email, password);
    }

    /**
     * Generates a unique email address to avoid conflicts between test runs.
     *
     * @return unique email string
     */
    protected String uniqueEmail() {
        return "test-" + System.currentTimeMillis() + "@epm-test.com";
    }
}
