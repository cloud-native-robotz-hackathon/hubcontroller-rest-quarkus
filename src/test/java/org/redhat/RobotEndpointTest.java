package org.redhat;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.request;
import static org.hamcrest.CoreMatchers.is;

import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.JsonBody;

import io.quarkiverse.mockserver.test.InjectMockServerClient;
import io.quarkiverse.mockserver.test.MockServerTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@QuarkusTest
@QuarkusTestResource(MockServerTestResource.class)
class RobotEndpointTest {

@InjectMockServerClient
    MockServerClient mockServerClient;


    @Test
    void testRobotEndpointStatus() {
        given()
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
                .withPath("/test/remote_status")
                .withMethod("POST"))
                .respond(
                   httpRequest -> response()
                                  .withStatusCode(200)
                                  .withHeader("Content-Type", "text/html")
                                  .withBody("OK")
                );
    
        given()
          .when().get("/robot/remote_status")
          .then()
             .statusCode(200)
             .body(is("OK"));
    }

}