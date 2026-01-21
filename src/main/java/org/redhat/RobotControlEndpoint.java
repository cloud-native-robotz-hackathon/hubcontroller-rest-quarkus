package org.redhat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.jboss.resteasy.reactive.RestQuery;

@Path("/control")
@ApplicationScoped
public class RobotControlEndpoint {

    // Generated once at application startup, stays the same for the application lifetime
    private final String eventId = UUID.randomUUID().toString();

    // Namespace where robot connection token request secrets are created
    private static final String ROBOT_NAMESPACE = "robot";

    // Skupper label for connection token request
    private static final String SKUPPER_TYPE_LABEL = "skupper.io/type";
    private static final String CONNECTION_TOKEN_REQUEST = "connection-token-request";

    @Inject
    RobotStatusController robotStatusController;

    @Inject
    OpenShiftClient openShiftClient;

    @GET
    @Path("/eventId")
    @Operation(summary = "Returns a unique event ID and optionally registers a new robot. Creates a Skupper connection token request secret if it doesn't exist.")
    @Produces(MediaType.TEXT_PLAIN)
    public String getEventId(
            @Parameter(description = "Robot name to register (optional)", required = false) 
            @RestQuery("robot_name") String robotName) {
        
        if (robotName != null && !robotName.isBlank()) {
            boolean registered = robotStatusController.registerRobot(robotName);
            if (registered) {
                System.out.println("Registered robot '" + robotName + "' with eventId: " + eventId);
            } else {
                System.out.println("Robot '" + robotName + "' already registered, returning eventId: " + eventId);
            }

            // Check if secret exists in the robot namespace, create if not
            ensureRobotSecretExists(robotName);
        }
        
        return eventId;
    }

    /**
     * Ensures a Skupper connection token request secret exists for the robot.
     * If the secret doesn't exist, it creates one with the skupper.io/type label.
     */
    private void ensureRobotSecretExists(String robotName) {
        try {
            // Check if secret already exists
            Secret existingSecret = openShiftClient.secrets()
                    .inNamespace(ROBOT_NAMESPACE)
                    .withName(robotName)
                    .get();

            if (existingSecret != null) {
                System.out.println("Secret '" + robotName + "' already exists in namespace '" + ROBOT_NAMESPACE + "'");
                return;
            }

            // Create new secret with Skupper connection token request label
            Secret newSecret = new SecretBuilder()
                    .withNewMetadata()
                        .withName(robotName)
                        .withNamespace(ROBOT_NAMESPACE)
                        .addToLabels(SKUPPER_TYPE_LABEL, CONNECTION_TOKEN_REQUEST)
                    .endMetadata()
                    .build();

            openShiftClient.secrets()
                    .inNamespace(ROBOT_NAMESPACE)
                    .resource(newSecret)
                    .create();

            System.out.println("Created Skupper connection token request secret '" + robotName + "' in namespace '" + ROBOT_NAMESPACE + "'");

        } catch (Exception e) {
            System.err.println("Error ensuring secret exists for robot '" + robotName + "': " + e.getMessage());
            e.printStackTrace();
        }
    }

    @GET
    @Path("/getToken")
    @Operation(summary = "Returns the base64 encoded secret for a robot from the OpenShift cluster. The robot_name is used as both the namespace and secret name.")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getToken(
            @Parameter(description = "Robot name (used as namespace and secret name)", required = true) 
            @RestQuery("robot_name") String robotName) {
        
        if (robotName == null || robotName.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("robot_name query parameter is required")
                    .build();
        }

        System.out.println("Fetching secret '" + robotName + "' in namespace '" + robotName + "'");

        try {
            // Fetch the secret from OpenShift (robot_name is both namespace and secret name)
            Secret secret = openShiftClient.secrets()
                    .inNamespace(robotName)
                    .withName(robotName)
                    .get();

            if (secret == null) {
                System.err.println("Secret not found for robot: " + robotName);
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Secret not found for robot: " + robotName)
                        .build();
            }

            // Get the secret data and encode the entire content as base64
            Map<String, String> secretData = secret.getData();
            
            if (secretData == null || secretData.isEmpty()) {
                System.err.println("Secret data is empty for robot: " + robotName);
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Secret data is empty for robot: " + robotName)
                        .build();
            }

            // Convert secret data to a string representation and encode as base64
            // The secret data values are already base64 encoded by Kubernetes
            // We'll return the entire secret data as a JSON-like string, base64 encoded
            String secretContent = secretData.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("\n"));
            
            String base64Encoded = Base64.getEncoder()
                    .encodeToString(secretContent.getBytes(StandardCharsets.UTF_8));

            System.out.println("Successfully retrieved secret for robot: " + robotName);
            return Response.ok(base64Encoded).build();

        } catch (Exception e) {
            System.err.println("Error fetching secret for robot '" + robotName + "': " + e.getMessage());
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error fetching secret: " + e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/podLogs")
    @Operation(summary = "Returns the latest logs from a robot's pod. The robot_name is used as the namespace and pod name prefix.")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getPodLogs(
            @Parameter(description = "Robot name (used as namespace)", required = true) 
            @RestQuery("robot_name") String robotName,
            @Parameter(description = "Number of log lines to return (default 100)", required = false) 
            @RestQuery("lines") Integer lines) {
        
        if (robotName == null || robotName.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("robot_name query parameter is required")
                    .build();
        }

        int tailLines = (lines != null && lines > 0) ? lines : 100;
        
        System.out.println("Fetching pod logs for robot '" + robotName + "' (last " + tailLines + " lines)");

        try {
            // Find the pod in the robot's namespace
            // Look for pods with name starting with the robot name or containing it
            var pods = openShiftClient.pods()
                    .inNamespace(robotName)
                    .list()
                    .getItems();

            if (pods == null || pods.isEmpty()) {
                System.err.println("No pods found in namespace: " + robotName);
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("No pods found in namespace: " + robotName)
                        .build();
            }

            // Try to find a running pod, preferring one that matches the robot name
            var targetPod = pods.stream()
                    .filter(pod -> "Running".equals(pod.getStatus().getPhase()))
                    .filter(pod -> pod.getMetadata().getName().contains(robotName) 
                            || pod.getMetadata().getName().contains("robot"))
                    .findFirst()
                    .orElse(pods.stream()
                            .filter(pod -> "Running".equals(pod.getStatus().getPhase()))
                            .findFirst()
                            .orElse(pods.get(0)));

            String podName = targetPod.getMetadata().getName();
            System.out.println("Found pod: " + podName + " in namespace: " + robotName);

            // Get the logs from the pod
            String logs = openShiftClient.pods()
                    .inNamespace(robotName)
                    .withName(podName)
                    .tailingLines(tailLines)
                    .getLog();

            if (logs == null || logs.isEmpty()) {
                logs = "No logs available for pod: " + podName;
            }

            System.out.println("Successfully retrieved " + logs.split("\n").length + " log lines for robot: " + robotName);
            return Response.ok(logs).build();

        } catch (Exception e) {
            System.err.println("Error fetching pod logs for robot '" + robotName + "': " + e.getMessage());
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error fetching logs: " + e.getMessage())
                    .build();
        }
    }
}
