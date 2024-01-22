package org.redhat;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.info.Info;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;

@Path("/robot")
@OpenAPIDefinition(info = @Info(title = "Robot API", version = "1.0.0")

)
public class RobotEndpoint {

    @GET
    @Path("/status")
    @Operation(summary = "Checks the Status of the API")
    @Produces("text/html")
    public String status(@QueryParam("user_key") String userKey) {
        System.out.println(userKey + ": Status called");
        return "OK";
    }

    @GET
    @Path("/remote_status")
    @Operation(summary = "Checks the status of connected robot")
    @Produces("text/html")
    public String remoteStatus(@QueryParam("user_key") String userKey) throws URISyntaxException, IOException, InterruptedException {
        System.out.println(userKey + ": Remote Status called");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("test/remote_status"))
                //.version(HttpClient.Version.HTTP_2)
                .GET()
                .build();

        HttpResponse<String> response = HttpClient
        .newBuilder().build().send(request, BodyHandlers.ofString());        

        return response.body();
    }
}
