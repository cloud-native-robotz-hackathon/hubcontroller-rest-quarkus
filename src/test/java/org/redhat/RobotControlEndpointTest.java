package org.redhat;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.filter.log.ResponseLoggingFilter;

@QuarkusTest
class RobotControlEndpointTest {

        // UUID pattern: 8-4-4-4-12 hex characters
        private static final String UUID_PATTERN = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

        // Default credentials for basic auth (matches application.properties)
        private static final String AUTH_USER = "admin";
        private static final String AUTH_PASSWORD = "robotadmin";

        static {
                RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
                RestAssured.filters(new ResponseLoggingFilter());
        }

        @Test
        void testEventIdEndpointReturnsUUID() {
                given()
                                .auth().basic(AUTH_USER, AUTH_PASSWORD)
                                .when().get("/control/eventId")
                                .then()
                                .statusCode(200)
                                .body(notNullValue())
                                .body(matchesPattern(UUID_PATTERN));
        }

        @Test
        void testEventIdEndpointReturnsSameValueOnMultipleCalls() {
                // First call
                String firstEventId = given()
                                .auth().basic(AUTH_USER, AUTH_PASSWORD)
                                .when().get("/control/eventId")
                                .then()
                                .statusCode(200)
                                .extract().body().asString();

                // Second call
                String secondEventId = given()
                                .auth().basic(AUTH_USER, AUTH_PASSWORD)
                                .when().get("/control/eventId")
                                .then()
                                .statusCode(200)
                                .extract().body().asString();

                // Should return the same UUID
                assertEquals(firstEventId, secondEventId, "Event ID should remain constant across multiple calls");
        }

        @Test
        void testEventIdEndpointRegistersRobot() {
                // Register a new robot and verify eventId is returned
                given()
                                .auth().basic(AUTH_USER, AUTH_PASSWORD)
                                .queryParam("robot_name", "test_robot")
                                .when().get("/control/eventId")
                                .then()
                                .statusCode(200)
                                .body(notNullValue())
                                .body(matchesPattern(UUID_PATTERN));

                // Verify the robot can now be used (won't return "Robot Not Registered")
                // Note: /robot/* endpoints don't require authentication
                given()
                                .queryParam("user_key", "test_robot")
                                .when().get("/robot/status")
                                .then()
                                .statusCode(200)
                                .body(org.hamcrest.CoreMatchers.is("OK"));
        }

        @Test
        void testControlEndpointRequiresAuthentication() {
                // Without auth, should get 401
                given()
                                .when().get("/control/eventId")
                                .then()
                                .statusCode(401);
        }
}
