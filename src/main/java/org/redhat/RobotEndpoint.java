package org.redhat;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.info.Info;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;

@Path("/robot")
@OpenAPIDefinition(info = @Info(title = "Robot API", version = "1.0.0")

)
public class RobotEndpoint {

    @ConfigProperty(name = "quarkus.mockserver.endpoint")
    String mockServerEndpoint;

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
    public String remoteStatus(@QueryParam("user_key") String userKey)
            throws URISyntaxException, IOException, InterruptedException {
        System.out.println(userKey + ": Remote Status called");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(getRobotURLFromConfigMap(userKey) + "/remote_status"))
                .GET()
                .build();
        HttpResponse<String> response = HttpClient
                .newBuilder().build().send(request, BodyHandlers.ofString());

        return response.body();
    }

    @POST
    @Path("/forward/{length_in_cm}")
    @Operation(summary = "Drives the robot forward by the indicated cm")
    @Produces("text/html")
    public String forward(@QueryParam("user_key") String userKey, @PathParam("length_in_cm") Integer lengthInCm)
            throws URISyntaxException, IOException, InterruptedException {

        System.out.println(userKey + ": forward called -> " + lengthInCm);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(getRobotURLFromConfigMap(userKey) + "/forward/" + lengthInCm))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = HttpClient
                .newBuilder().build().send(request, BodyHandlers.ofString());

        return response.body();
    }

    @POST
    @Path("/backward/{length_in_cm}")
    @Operation(summary = "Drives the robot backward by the indicated cm")
    @Produces("text/html")
    public String backward(@QueryParam("user_key") String userKey, @PathParam("length_in_cm") Integer lengthInCm)
            throws URISyntaxException, IOException, InterruptedException {

        System.out.println(userKey + ": backward called -> " + lengthInCm);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(getRobotURLFromConfigMap(userKey) + "/backward/" + lengthInCm))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = HttpClient
                .newBuilder().build().send(request, BodyHandlers.ofString());

        return response.body();
    }

    @POST
    @Path("/left/{degrees}")
    @Operation(summary = "Turns the robot left by the indicated degrees (positive)")
    @Produces("text/html")
    public String left(@QueryParam("user_key") String userKey, @PathParam("degrees") Integer degrees)
            throws URISyntaxException, IOException, InterruptedException {

        System.out.println(userKey + ": left called -> " + degrees);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(getRobotURLFromConfigMap(userKey) + "/left/" + degrees))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = HttpClient
                .newBuilder().build().send(request, BodyHandlers.ofString());

        return response.body();
    }

    @POST
    @Path("/right/{degrees}")
    @Operation(summary = "Turns the robot right by the indicated degrees (positive)")
    @Produces("text/html")
    public String right(@QueryParam("user_key") String userKey, @PathParam("degrees") Integer degrees)
            throws URISyntaxException, IOException, InterruptedException {

        System.out.println(userKey + ": right called -> " + degrees);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(getRobotURLFromConfigMap(userKey) + "/right/" + degrees))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = HttpClient
                .newBuilder().build().send(request, BodyHandlers.ofString());

        return response.body();
    }

    @GET
    @Path("/camera")
    @Operation(summary = "Get the current image from the camera")
    @Produces("text/html")
    public String camera(@QueryParam("user_key") String userKey)
            throws URISyntaxException, IOException, InterruptedException {

        System.out.println(userKey + ": camera called");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(getRobotURLFromConfigMap(userKey) + "/camera"))
                .GET()
                .build();
        HttpResponse<String> response = HttpClient
                .newBuilder().build().send(request, BodyHandlers.ofString());

        return response.body();
    }

    private String getRobotURLFromConfigMap(String token) {

        if (mockServerEndpoint != null)
            return mockServerEndpoint;

        String apiTokenMap = System.getenv().getOrDefault("MAP", "{}");

        System.out.println("Robot token config map json -> " + apiTokenMap);

        ObjectReader reader = new ObjectMapper().readerFor(Map.class);

        String hostName = null;

        try {
            Map<String, String> map = reader.readValue(apiTokenMap);

            System.out.println("Robot token map -> " + map);

            System.out.println("Checking for token -> " + token);

            hostName = map.get(token);
            if (hostName != null)
                System.out.println("Got hostname match -> " + hostName);
            else {
                System.out.println("no match, defaulting to token as hostname ->  " + hostName);
                hostName = token;
            }

        } catch (IOException e) {
            System.err.println("Error parsing Robot ConfigMap to JSON -> the format correct?");
            e.printStackTrace();
        }

        return "http://" + hostName + ":5000";
    }
}
