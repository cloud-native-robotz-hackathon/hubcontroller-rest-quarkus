package org.redhat;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;

import io.quarkiverse.mockserver.test.InjectMockServerClient;
import io.quarkiverse.mockserver.test.MockServerTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.filter.log.ResponseLoggingFilter;

@QuarkusTest
@QuarkusTestResource(MockServerTestResource.class)

class RobotEndpointTest {

        static {
                RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
                RestAssured.filters(new ResponseLoggingFilter());
        }

        @InjectMockServerClient
        MockServerClient mockServerClient;

        @Test
        void testRobotEndpointStatus() {
                given().queryParam("user_key", "data")
                                .when().get("/robot/status")
                                .then()
                                .statusCode(200)
                                .body(is("OK"));
        }

        @Test
        void testRobotEndpointRemoteStatus() {

                // create mock rest endpoint
                mockServerClient
                                .when(request()
                                                .withPath("/")
                                                .withMethod("GET"))
                                .respond(
                                                httpRequest -> response()
                                                                .withStatusCode(200)
                                                                .withHeader("Content-Type", "text/html")
                                                                .withBody("OK"));

                given().queryParam("user_key", "data")
                                .when().get("/robot/remote_status")
                                .then()
                                .statusCode(200)
                                .body(is("OK"));
        }

        @Test
        void testRobotEndpointForward() {

                // create mock rest endpoint
                mockServerClient
                                .when(request()
                                                .withPath("/forward/10")
                                                .withMethod("POST"))
                                .respond(
                                                httpRequest -> response()
                                                                .withStatusCode(200)
                                                                .withHeader("Content-Type", "text/html")
                                                                .withBody("OK"));

                given().formParam("user_key", "data")
                                .when().post("/robot/forward/10")
                                .then()
                                .statusCode(200)
                                .body(is("OK"));
        }

        @Test
        void testRobotEndpointBackward() {

                // create mock rest endpoint
                mockServerClient
                                .when(request()
                                                .withPath("/backward/10")
                                                .withMethod("POST"))
                                .respond(
                                                httpRequest -> response()
                                                                .withStatusCode(200)
                                                                .withHeader("Content-Type", "text/html")
                                                                .withBody("OK"));

                given().formParam("user_key", "data")
                                .when().post("/robot/backward/10")
                                .then()
                                .statusCode(200)
                                .body(is("OK"));
        }

        @Test
        void testRobotEndpointLeft() {

                // create mock rest endpoint
                mockServerClient
                                .when(request()
                                                .withPath("/left/10")
                                                .withMethod("POST"))
                                .respond(
                                                httpRequest -> response()
                                                                .withStatusCode(200)
                                                                .withHeader("Content-Type", "text/html")
                                                                .withBody("OK"));

                given().formParam("user_key", "data")
                                .when().post("/robot/left/10")
                                .then()
                                .statusCode(200)
                                .body(is("OK"));
        }

        @Test
        void testRobotEndpointRight() {

                // create mock rest endpoint
                mockServerClient
                                .when(request()
                                                .withPath("/right/10")
                                                .withMethod("POST"))
                                .respond(
                                                httpRequest -> response()
                                                                .withStatusCode(200)
                                                                .withHeader("Content-Type", "text/html")
                                                                .withBody("OK"));

                given().formParam("user_key", "data")
                                .when().post("/robot/right/10")
                                .then()
                                .statusCode(200)
                                .body(is("OK"));
        }

        @Test
        void testRobotEndpointCamera() {

                // create mock rest endpoint
                mockServerClient
                                .when(request()
                                                .withPath("/camera")
                                                .withMethod("GET"))
                                .respond(
                                                httpRequest -> response()
                                                                .withStatusCode(200)
                                                                .withHeader("Content-Type", "text/html")
                                                                .withBody(
                                                                                "iVBORw0KGgoAAAANSUhEUgAAAQAAAAEACAIAAADTED8xAAADMElEQVR4nOzVwQnAIBQFQYXff81RUkQCOyDj1YOPnbXWPmeTRef+/3O/OyBjzh3CD95BfqICMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMO0TAAD//2Anhf4QtqobAAAAAElFTkSuQmCC"));

                given().queryParam("user_key", "data")
                                .when().get("/robot/camera")
                                .then()
                                .statusCode(200)
                                .body(is(
                                                "iVBORw0KGgoAAAANSUhEUgAAAQAAAAEACAIAAADTED8xAAADMElEQVR4nOzVwQnAIBQFQYXff81RUkQCOyDj1YOPnbXWPmeTRef+/3O/OyBjzh3CD95BfqICMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMK0CMO0TAAD//2Anhf4QtqobAAAAAElFTkSuQmCC"));
        }

}