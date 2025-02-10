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
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import io.quarkus.runtime.LaunchMode;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

@Path("/robot")
@OpenAPIDefinition(info = @Info(title = "Robot Control API", version = "1.0.0")

)

// The main controller, that passes RESTful calls on to the matching robot API
public class RobotEndpoint {

        private static final String RESPONSE_OK = "OK";

        // The robot token being sent das parameter by the users
        private static final String API_TOKEN = "user_key";

        // endpoint for the Rest mockserver for automated testing
        @ConfigProperty(name = "quarkus.mockserver.endpoint")
        String mockServerEndpoint;

        // the json map for specific mapping from Robot Token to Robot Address
        @ConfigProperty(name = "robot.map", defaultValue = "{}")
        String robotMap;

        // The dashboard backend
        @Inject
        DashBoard dashBoard;

        @Inject
        RobotStatusController robotStatusController;

        @GET
        @Path("/status")
        @Operation(summary = "Checks the status of the HubController")
        @Produces("text/html")
        public String status(
                        @Parameter(description = "The token of the robot", required = false) @RestQuery(API_TOKEN) String userKey) {

                System.out.println("Status called -> " + userKey);
                robotStatusController.addRobot(userKey);
                return RESPONSE_OK;
        }

        @GET
        @Path("/remote_status")
        @Operation(summary = "Checks the status of connected robot")
        @Produces("text/html")
        public String remoteStatus(
                        @Parameter(description = "The token of the robot", required = true) @RestQuery(API_TOKEN) String userKey)
                        throws URISyntaxException, IOException, InterruptedException {
                System.out.println(userKey + ": Remote Status called");

                URI url = new URI(getRobotURLFromConfigMap(userKey));
                System.out.println("Calling -> " + url);

                if (robotStatusController.addOrUpdateRobot(userKey, "remote_status"))
                        return "Robot Disconnected";

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(url)
                                .GET()
                                .build();
                HttpResponse<String> response = HttpClient
                                .newBuilder().build().send(request, BodyHandlers.ofString());

                robotStatusController.setRobotStatus(userKey, true);

                System.out.println("Response -> " + response.body());
                return response.body();
        }

        @GET
        @Path("/distance")
        @Operation(summary = "Checks the distance")
        @Produces("text/html")
        public String distance(
                        @Parameter(description = "The token of the robot", required = true) @RestQuery(API_TOKEN) String userKey)
                        throws URISyntaxException, IOException, InterruptedException {
                System.out.println(userKey + ": Distance Status called");

                URI url = new URI(getRobotURLFromConfigMap(userKey));
                System.out.println("Calling -> " + url);

                if (robotStatusController.addOrUpdateRobot(userKey, "distance"))
                        return "Robot Disconnected";

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(new URI(url + "/distance"))
                                .GET()
                                .build();
                HttpResponse<String> response = HttpClient
                                .newBuilder().build().send(request, BodyHandlers.ofString());

                robotStatusController.setRobotStatus(userKey, true);

                System.out.println("Response -> " + response.body());
                return response.body();
        }

        @POST
        @Path("/forward/{length_in_cm}")
        @Operation(summary = "Drives the robot forward by the indicated cm")
        @Produces("text/html")
        public String forward(
                        @Parameter(description = "The token of the robot", required = true) @RestForm(API_TOKEN) String userKey,
                        @Parameter(description = "The length to drive the robot forward", required = true) @RestPath("length_in_cm") Integer lengthInCm)
                        throws URISyntaxException, IOException, InterruptedException {

                System.out.println(userKey + ": forward called -> " + lengthInCm);

                if (robotStatusController.addOrUpdateRobot(userKey, "forward"))
                        return "Robot Disconnected";

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
        public String backward(
                        @Parameter(description = "The token of the robot", required = true) @RestForm(API_TOKEN) String userKey,
                        @Parameter(description = "The length to drive to robot backward", required = true) @RestPath("length_in_cm") Integer lengthInCm)
                        throws URISyntaxException, IOException, InterruptedException {

                System.out.println(userKey + ": backward called -> " + lengthInCm);
                if (robotStatusController.addOrUpdateRobot(userKey, "backward"))
                        return "Robot Disconnected";
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
        public String left(
                        @Parameter(description = "The token of the robot", required = true) @RestForm(API_TOKEN) String userKey,
                        @Parameter(description = "Degrees to turn the robot left", required = true) @RestPath("degrees") Integer degrees)
                        throws URISyntaxException, IOException, InterruptedException {

                System.out.println(userKey + ": left called -> " + degrees);
                if (robotStatusController.addOrUpdateRobot(userKey, "left"))
                        return "Robot Disconnected";
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
        public String right(
                        @Parameter(description = "The token of the robot", required = true) @RestForm(API_TOKEN) String userKey,
                        @Parameter(description = "Degrees to turn the robot right", required = true) @RestPath("degrees") Integer degrees)
                        throws URISyntaxException, IOException, InterruptedException {

                System.out.println(userKey + ": right called -> " + degrees);
                if (robotStatusController.addOrUpdateRobot(userKey, "right"))
                        return "Robot Disconnected";
                HttpRequest request = HttpRequest.newBuilder()
                                .uri(new URI(getRobotURLFromConfigMap(userKey) + "/right/" + degrees))
                                .POST(HttpRequest.BodyPublishers.noBody())
                                .build();
                HttpResponse<String> response = HttpClient
                                .newBuilder().build().send(request, BodyHandlers.ofString());

                return response.body();
        }

        @POST
        @Path("/disconnect/{robotId}")
        @Operation(summary = "Disconnect Robot")
        @Produces("text/html")
        public Boolean disconnect(
                        @Parameter(description = "The token of the robot", required = true) @RestPath("robotId") String robotShortId)
                        throws URISyntaxException, IOException, InterruptedException {

                System.out.println("disconnect called for robotId-> " + robotShortId);

                boolean isDisconnected = robotStatusController.disconnectRobot(robotShortId);

                return isDisconnected;
        }

        @POST
        @Path("/runapp/{robotId}")
        @Operation(summary = "Disconnect Robot")
        @Produces("text/html")
        public String runapp(
                        @Parameter(description = "The token of the robot", required = true) @RestPath("robotId") String robotShortId)
                        throws URISyntaxException, IOException, InterruptedException {

                System.out.println("runapp called for robotId- > " + robotShortId);

                String robotId = robotStatusController.findRobotByShortName(robotShortId).getName();

                System.out.println("runapp sesolving to robotId -> " + robotId);

                System.out.println("Calling -> " + "http://starterapp-python.robot-app.apps."+robotId+"/run");


                HttpRequest request = HttpRequest.newBuilder()
                                .uri(new URI("http://starterapp-python.robot-app.apps."+robotId+"/run"))
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
        public String camera(
                        @Parameter(description = "The token of the robot", required = true) @RestQuery(API_TOKEN) String userKey)
                        throws URISyntaxException, IOException, InterruptedException {

                System.out.println(userKey + ": camera called");
                if (robotStatusController.addOrUpdateRobot(userKey, "camera"))
                        return "Robot Disconnected";
                HttpRequest request = HttpRequest.newBuilder()
                                .uri(new URI(getRobotURLFromConfigMap(userKey) + "/camera"))
                                .GET()
                                .build();
                HttpResponse<String> response = HttpClient
                                .newBuilder().build().send(request, BodyHandlers.ofString());

                return response.body();
        }

        private String getRobotURLFromConfigMap(String token) {

                //System.out.println("Launchmode -> " + LaunchMode.current());
                if (mockServerEndpoint != null && LaunchMode.current().equals(LaunchMode.TEST)) {
                        System.out.println("Mock Endpoint -> " + mockServerEndpoint);
                        return mockServerEndpoint;
                }

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
                                hostName = token;
                                System.out.println("No match, defaulting to token as hostname ->  " + hostName);

                        }

                } catch (IOException e) {
                        System.err.println("Error parsing Robot ConfigMap to JSON -> is the format correct?");
                        e.printStackTrace();
                }

                String hostUrl = "http://" + hostName + ":5000";
                System.out.println("Using url -> " + hostUrl);

                return hostUrl;
        }
}
