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

        /**
         * Sanitizes a user key for safe use.
         * Returns null if the key is invalid.
         */
        private String sanitizeUserKey(String userKey) {
                return InputSanitizer.sanitizeRobotName(userKey);
        }

        @GET
        @Path("/status")
        @Operation(summary = "Checks the status of the HubController")
        @Produces("text/html")
        public String status(
                        @Parameter(description = "The token of the robot", required = false) @RestQuery(API_TOKEN) String userKey) {

                String sanitizedKey = sanitizeUserKey(userKey);
                System.out.println("Status called -> " + sanitizedKey);
                if (sanitizedKey != null && !robotStatusController.robotExists(sanitizedKey)) {
                        return "Robot Not Registered";
                }
                return RESPONSE_OK;
        }

        @GET
        @Path("/remote_status")
        @Operation(summary = "Checks the status of connected robot")
        @Produces("text/html")
        public String remoteStatus(
                        @Parameter(description = "The token of the robot", required = true) @RestQuery(API_TOKEN) String userKey) {
                
                String sanitizedKey = sanitizeUserKey(userKey);
                if (sanitizedKey == null || !robotStatusController.robotExists(sanitizedKey))
                        return "Robot Not Registered";

                // Remote status calls don't count as operations - just check if disconnected
                if (robotStatusController.isRobotDisconnected(sanitizedKey))
                        return "Robot Disconnected";

                String urlString = getRobotURLFromConfigMap(sanitizedKey);
                try {
                        URI url = new URI(urlString);

                        HttpRequest request = HttpRequest.newBuilder()
                                        .uri(url)
                                        .GET()
                                        .build();
                        HttpResponse<String> response = HttpClient
                                        .newBuilder().build().send(request, BodyHandlers.ofString());

                        robotStatusController.setRobotStatus(sanitizedKey, true);

                        return response.body();
                } catch (Exception e) {
                        // Log connection errors concisely - these are expected when robot is offline
                        System.out.println(sanitizedKey + ": Connection failed to " + urlString + " - " + e.getClass().getSimpleName() + ": " + e.getMessage());
                        robotStatusController.setRobotStatus(sanitizedKey, false);
                        return "Connection Error";
                }
        }

        @GET
        @Path("/distance")
        @Operation(summary = "Checks the distance")
        @Produces("text/html")
        public String distance(
                        @Parameter(description = "The token of the robot", required = true) @RestQuery(API_TOKEN) String userKey)
                        throws URISyntaxException, IOException, InterruptedException {
                String sanitizedKey = sanitizeUserKey(userKey);
                System.out.println(sanitizedKey + ": Distance Status called");

                if (sanitizedKey == null || !robotStatusController.robotExists(sanitizedKey))
                        return "Robot Not Registered";

                URI url = new URI(getRobotURLFromConfigMap(sanitizedKey));
                System.out.println("Calling -> " + url);

                if (robotStatusController.updateRobot(sanitizedKey, "distance"))
                        return "Robot Disconnected";

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(new URI(url + "/distance"))
                                .GET()
                                .build();
                HttpResponse<String> response = HttpClient
                                .newBuilder().build().send(request, BodyHandlers.ofString());

                robotStatusController.setRobotStatus(sanitizedKey, true);

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

                String sanitizedKey = sanitizeUserKey(userKey);
                System.out.println(sanitizedKey + ": forward called -> " + lengthInCm);

                if (sanitizedKey == null || !robotStatusController.robotExists(sanitizedKey))
                        return "Robot Not Registered";

                if (robotStatusController.updateRobot(sanitizedKey, "forward"))
                        return "Robot Disconnected";

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(new URI(getRobotURLFromConfigMap(sanitizedKey) + "/forward/" + lengthInCm))
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

                String sanitizedKey = sanitizeUserKey(userKey);
                System.out.println(sanitizedKey + ": backward called -> " + lengthInCm);

                if (sanitizedKey == null || !robotStatusController.robotExists(sanitizedKey))
                        return "Robot Not Registered";

                if (robotStatusController.updateRobot(sanitizedKey, "backward"))
                        return "Robot Disconnected";
                HttpRequest request = HttpRequest.newBuilder()
                                .uri(new URI(getRobotURLFromConfigMap(sanitizedKey) + "/backward/" + lengthInCm))
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

                String sanitizedKey = sanitizeUserKey(userKey);
                System.out.println(sanitizedKey + ": left called -> " + degrees);

                if (sanitizedKey == null || !robotStatusController.robotExists(sanitizedKey))
                        return "Robot Not Registered";

                if (robotStatusController.updateRobot(sanitizedKey, "left"))
                        return "Robot Disconnected";
                HttpRequest request = HttpRequest.newBuilder()
                                .uri(new URI(getRobotURLFromConfigMap(sanitizedKey) + "/left/" + degrees))
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

                String sanitizedKey = sanitizeUserKey(userKey);
                System.out.println(sanitizedKey + ": right called -> " + degrees);

                if (sanitizedKey == null || !robotStatusController.robotExists(sanitizedKey))
                        return "Robot Not Registered";

                if (robotStatusController.updateRobot(sanitizedKey, "right"))
                        return "Robot Disconnected";
                HttpRequest request = HttpRequest.newBuilder()
                                .uri(new URI(getRobotURLFromConfigMap(sanitizedKey) + "/right/" + degrees))
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

                String sanitizedId = sanitizeUserKey(robotShortId);
                System.out.println("disconnect called for robotId-> " + sanitizedId);

                if (sanitizedId == null) return false;
                boolean isDisconnected = robotStatusController.disconnectRobot(sanitizedId);

                return isDisconnected;
        }

        @POST
        @Path("/runapp/{robotId}")
        @Operation(summary = "Run the application on the robot")
        @Produces("text/html")
        public String runapp(
                        @Parameter(description = "The token of the robot", required = true) @RestPath("robotId") String robotShortId)
                        throws URISyntaxException, IOException, InterruptedException {

                String sanitizedId = sanitizeUserKey(robotShortId);
                System.out.println("runapp called for robotId- > " + sanitizedId);

                if (sanitizedId == null) return "Invalid robot ID";
                Robot robot = robotStatusController.findRobotByShortName(sanitizedId);
                if (robot == null) return "Robot Not Found";
                String robotId = robot.getName();

                System.out.println("runapp resolving to robotId -> " + robotId);

                System.out.println("Calling -> " + "http://" + robotId
                                + ".robot.svc.cluster.local./run  with header -> Host: starterapp-python-robot-app.apps."
                                + robotId);

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(new URI("http://" + robotId + ".robot.svc.cluster.local./run"))
                                .POST(HttpRequest.BodyPublishers.noBody())
                                .headers("Host", "starterapp-python-robot-app.apps." + robotId)
                                .build();
                HttpResponse<String> response = HttpClient
                                .newBuilder().build().send(request, BodyHandlers.ofString());

                return response.body();
        }

        @POST
        @Path("/stopapp/{robotId}")
        @Operation(summary = "Stop the application on the robot")
        @Produces("text/html")
        public String stopapp(
                        @Parameter(description = "The token of the robot", required = true) @RestPath("robotId") String robotShortId)
                        throws URISyntaxException, IOException, InterruptedException {

                String sanitizedId = sanitizeUserKey(robotShortId);
                System.out.println("stopapp called for robotId- > " + sanitizedId);

                if (sanitizedId == null) return "Invalid robot ID";
                Robot robot = robotStatusController.findRobotByShortName(sanitizedId);
                if (robot == null) return "Robot Not Found";
                String robotId = robot.getName();

                System.out.println("stopapp resolving to robotId -> " + robotId);

                System.out.println("Calling -> " + "http://" + robotId
                                + ".robot.svc.cluster.local./stop  with header -> Host: starterapp-python-robot-app.apps."
                                + robotId);

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(new URI("http://" + robotId + ".robot.svc.cluster.local./stop"))
                                .POST(HttpRequest.BodyPublishers.noBody())
                                .headers("Host", "starterapp-python-robot-app.apps." + robotId)
                                .build();
                HttpResponse<String> response = HttpClient
                                .newBuilder().build().send(request, BodyHandlers.ofString());

                return response.body();
        }

        @POST
        @Path("/led_on/{color}")
        @Operation(summary = "Turn on robot LED eyes with the specified color (red, green, blue)")
        @Produces("text/html")
        public String ledOn(
                        @Parameter(description = "The token of the robot", required = true) @RestForm(API_TOKEN) String userKey,
                        @Parameter(description = "LED color: red, green, or blue", required = true) @RestPath("color") String color)
                        throws URISyntaxException, IOException, InterruptedException {

                String sanitizedKey = sanitizeUserKey(userKey);
                System.out.println(sanitizedKey + ": led_on called -> " + color);

                if (sanitizedKey == null || !robotStatusController.robotExists(sanitizedKey))
                        return "Robot Not Registered";

                if (robotStatusController.updateRobot(sanitizedKey, "led_on"))
                        return "Robot Disconnected";

                String sanitizedColor = color.toLowerCase().replaceAll("[^a-z]", "");

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(new URI(getRobotURLFromConfigMap(sanitizedKey) + "/led_on/" + sanitizedColor))
                                .POST(HttpRequest.BodyPublishers.noBody())
                                .build();
                HttpResponse<String> response = HttpClient
                                .newBuilder().build().send(request, BodyHandlers.ofString());

                return response.body();
        }

        @POST
        @Path("/led_off")
        @Operation(summary = "Turn off robot LED eyes")
        @Produces("text/html")
        public String ledOff(
                        @Parameter(description = "The token of the robot", required = true) @RestForm(API_TOKEN) String userKey)
                        throws URISyntaxException, IOException, InterruptedException {

                String sanitizedKey = sanitizeUserKey(userKey);
                System.out.println(sanitizedKey + ": led_off called");

                if (sanitizedKey == null || !robotStatusController.robotExists(sanitizedKey))
                        return "Robot Not Registered";

                if (robotStatusController.updateRobot(sanitizedKey, "led_off"))
                        return "Robot Disconnected";

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(new URI(getRobotURLFromConfigMap(sanitizedKey) + "/led_off"))
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
                        @Parameter(description = "The token of the robot", required = true) @RestQuery(API_TOKEN) String userKey) {

                String sanitizedKey = sanitizeUserKey(userKey);
                if (sanitizedKey == null || !robotStatusController.robotExists(sanitizedKey))
                        return "Robot Not Registered";

                // Camera calls don't count as operations - just check if disconnected
                if (robotStatusController.isRobotDisconnected(sanitizedKey))
                        return "Robot Disconnected";

                String urlString = getRobotURLFromConfigMap(sanitizedKey) + "/camera";
                try {
                        HttpRequest request = HttpRequest.newBuilder()
                                        .uri(new URI(urlString))
                                        .GET()
                                        .build();
                        HttpResponse<String> response = HttpClient
                                        .newBuilder().build().send(request, BodyHandlers.ofString());

                        return response.body();
                } catch (Exception e) {
                        // Log connection errors concisely - these are expected when robot is offline
                        System.out.println(sanitizedKey + ": Camera connection failed to " + urlString + " - " + e.getClass().getSimpleName() + ": " + e.getMessage());
                        return "Connection Error";
                }
        }

        private String getRobotURLFromConfigMap(String token) {

                // System.out.println("Launchmode -> " + LaunchMode.current());
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
                                hostName = addHostExtension(token);
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

        private String addHostExtension(String host) {
                if (host.contains(".")) {
                        System.out.println("Token propably IP, keeping as is");
                        return host;
                } else {
                        System.out.println("Adding host extension -> .robot.svc.cluster.local.");
                        return host + ".robot.svc.cluster.local.";
                }

        }
}
