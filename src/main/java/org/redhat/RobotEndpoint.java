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
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import io.quarkus.runtime.LaunchMode;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

@Path("/robot")
@OpenAPIDefinition(info = @Info(title = "Robot Control API", version = "1.0.0")

)

// The main controller, that passes RESTful calls on to the matching robot API
public class RobotEndpoint {

        @ConfigProperty(name = "quarkus.mockserver.endpoint")
        String mockServerEndpoint;

        @ConfigProperty(name = "robot.map", defaultValue = "{}")
        String robotMap;

        final String API_TOKEN = "user_key";

        @GET
        @Path("/status")
        @Operation(summary = "Checks the Status of the API")
        @Produces("text/html")
        public String status(@RestQuery(API_TOKEN) String userKey) {
                System.out.println(userKey + ": Status called");
                return "OK";
        }

        @GET
        @Path("/remote_status")
        @Operation(summary = "Checks the status of connected robot")
        @Produces("text/html")
        public String remoteStatus(@RestQuery(API_TOKEN) String userKey)
                        throws URISyntaxException, IOException, InterruptedException {
                System.out.println(userKey + ": Remote Status called");

                URI url = new URI(getRobotURLFromConfigMap(userKey));
                System.out.println("Calling -> " + url);

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(url)
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
        public String forward(@RestForm(API_TOKEN) String userKey, @RestPath("length_in_cm") Integer lengthInCm)
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
        public String backward(@RestForm(API_TOKEN) String userKey, @RestPath("length_in_cm") Integer lengthInCm)
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
        public String left(@RestForm(API_TOKEN) String userKey, @RestPath("degrees") Integer degrees)
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
        public String right(@RestForm(API_TOKEN) String userKey, @RestPath("degrees") Integer degrees)
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
        public String camera(@RestQuery(API_TOKEN) String userKey)
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

                System.out.println("Launchmode -> " + LaunchMode.current());
                if (mockServerEndpoint != null && LaunchMode.current().equals(LaunchMode.TEST))
                        return mockServerEndpoint;

                String apiTokenMap = System.getenv().getOrDefault("MAP", robotMap);

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
                                System.out.println("No match, defaulting to token as hostname ->  " + hostName);
                                hostName = token;
                        }

                } catch (IOException e) {
                        System.err.println("Error parsing Robot ConfigMap to JSON -> is the format correct?");
                        e.printStackTrace();
                }

                return "http://" + hostName + ":5000";
        }
}
