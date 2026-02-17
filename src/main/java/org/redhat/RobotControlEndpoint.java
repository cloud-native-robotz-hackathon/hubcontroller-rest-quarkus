package org.redhat;

import java.util.UUID;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.fabric8.openshift.client.OpenShiftClient;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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

    // Skupper site ConfigMap name
    private static final String SKUPPER_SITE_CONFIGMAP = "skupper-site";

    // Skupper site controller namespace
    private static final String OPENSHIFT_OPERATORS_NAMESPACE = "openshift-operators";

    @Inject
    RobotStatusController robotStatusController;

    @Inject
    OpenShiftClient openShiftClient;

    /**
     * Restart the skupper-site-controller pod on application startup.
     * This ensures the controller picks up any configuration changes.
     * Skipped in test and dev modes.
     */
    void onStart(@Observes StartupEvent ev) {
        // Skip skupper restart in test and dev modes
        if (LaunchMode.current() == LaunchMode.TEST || LaunchMode.current() == LaunchMode.DEVELOPMENT) {
            System.out.println("Skipping skupper-site-controller restart in " + LaunchMode.current() + " mode");
            return;
        }
        restartSkupperSiteController();
    }

    /**
     * Restarts the skupper-site-controller by deleting the pod.
     * The deployment will automatically recreate it.
     */
    private void restartSkupperSiteController() {
        try {
            System.out.println("Restarting skupper-site-controller in namespace '" + OPENSHIFT_OPERATORS_NAMESPACE + "'...");

            // Find and delete pods with the skupper-site-controller label
            var pods = openShiftClient.pods()
                    .inNamespace(OPENSHIFT_OPERATORS_NAMESPACE)
                    .withLabel("app.kubernetes.io/name", "skupper-site-controller")
                    .list()
                    .getItems();

            if (pods == null || pods.isEmpty()) {
                System.out.println("No skupper-site-controller pods found to restart");
                return;
            }

            for (Pod pod : pods) {
                String podName = pod.getMetadata().getName();
                System.out.println("Deleting skupper-site-controller pod: " + podName);
                
                openShiftClient.pods()
                        .inNamespace(OPENSHIFT_OPERATORS_NAMESPACE)
                        .withName(podName)
                        .delete();
                
                System.out.println("Successfully deleted pod: " + podName + " (will be recreated by deployment)");
            }

        } catch (Exception e) {
            System.err.println("Error restarting skupper-site-controller: " + e.getMessage());
            // Don't fail startup if we can't restart the controller
        }
    }

    @GET
    @Path("/eventId")
    @Operation(summary = "Returns a unique event ID and optionally registers a new robot. Creates a Skupper connection token request secret if it doesn't exist.")
    @Produces(MediaType.TEXT_PLAIN)
    public String getEventId(
            @Parameter(description = "Robot name to register", required = true) 
            @RestQuery("robot_name") String robotName) {
        
        if (robotName != null && !robotName.isBlank()) {
            boolean registered = robotStatusController.registerRobot(robotName);
            if (registered) {
                System.out.println("Registered robot '" + robotName + "' with eventId: " + eventId);
            } else {
                System.out.println("Robot '" + robotName + "' already registered, returning eventId: " + eventId);
            }

            // Skip OpenShift operations in test and dev modes
            if (LaunchMode.current() != LaunchMode.TEST && LaunchMode.current() != LaunchMode.DEVELOPMENT) {
                // Ensure namespace exists
                // ensureNamespaceExists();

                // Ensure Skupper site ConfigMap exists
                ensureSkupperSiteConfigMapExists();

                // Check if secret exists in the robot namespace, create if not
                ensureRobotSecretExists(robotName);
            }
        }
        
        return eventId;
    }

    @POST
    @Path("/initStatus")
    @Operation(summary = "Updates the initialization status for a robot. The status is displayed on the robot's dashboard tile.")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    public Response setInitStatus(
            @Parameter(description = "Robot name to update", required = true)
            @FormParam("robot_name") String robotName,
            @Parameter(description = "Current initialization status", required = true)
            @FormParam("status") String status) {

        if (robotName == null || robotName.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("robot_name form parameter is required")
                    .build();
        }

        if (status == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("status form parameter is required")
                    .build();
        }

        System.out.println("Setting init status for robot '" + robotName + "' to: " + status);

        boolean updated = robotStatusController.setRobotInitStatus(robotName, status);
        
        if (updated) {
            return Response.ok("Status updated for robot: " + robotName).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Robot not found: " + robotName)
                    .build();
        }
    }

    /**
     * Ensures the robot namespace exists.
     * Creates it if it doesn't exist.
     */
    private void ensureNamespaceExists() {
        try {
            Namespace existingNamespace = openShiftClient.namespaces()
                    .withName(ROBOT_NAMESPACE)
                    .get();

            if (existingNamespace != null) {
                System.out.println("Namespace '" + ROBOT_NAMESPACE + "' already exists");
                return;
            }

            // Create the namespace
            Namespace newNamespace = new NamespaceBuilder()
                    .withNewMetadata()
                        .withName(ROBOT_NAMESPACE)
                    .endMetadata()
                    .build();

            openShiftClient.namespaces()
                    .resource(newNamespace)
                    .create();

            System.out.println("Created namespace '" + ROBOT_NAMESPACE + "'");

        } catch (Exception e) {
            System.err.println("Error ensuring namespace exists: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Ensures the Skupper site ConfigMap exists in the robot namespace.
     * Creates it with the required Skupper configuration if it doesn't exist.
     */
    private void ensureSkupperSiteConfigMapExists() {
        try {
            ConfigMap existingConfigMap = openShiftClient.configMaps()
                    .inNamespace(ROBOT_NAMESPACE)
                    .withName(SKUPPER_SITE_CONFIGMAP)
                    .get();

            if (existingConfigMap != null) {
                System.out.println("ConfigMap '" + SKUPPER_SITE_CONFIGMAP + "' already exists in namespace '" + ROBOT_NAMESPACE + "'");
                return;
            }

            // Create the Skupper site ConfigMap with required configuration
            ConfigMap skupperSiteConfigMap = new ConfigMapBuilder()
                    .withNewMetadata()
                        .withName(SKUPPER_SITE_CONFIGMAP)
                        .withNamespace(ROBOT_NAMESPACE)
                    .endMetadata()
                    .addToData("cluster-permissions", "false")
                    .addToData("console", "true")
                    .addToData("console-authentication", "internal")
                    .addToData("console-password", "")
                    .addToData("console-user", "")
                    .addToData("enable-skupper-events", "true")
                    .addToData("flow-collector", "true")
                    .addToData("ingress", "route")
                    .addToData("name", "data-center")
                    .addToData("router-console", "false")
                    .addToData("router-logging", "")
                    .addToData("router-mode", "interior")
                    .addToData("service-controller", "true")
                    .addToData("service-sync", "true")
                    .build();

            openShiftClient.configMaps()
                    .inNamespace(ROBOT_NAMESPACE)
                    .resource(skupperSiteConfigMap)
                    .create();

            System.out.println("Created Skupper site ConfigMap '" + SKUPPER_SITE_CONFIGMAP + "' in namespace '" + ROBOT_NAMESPACE + "'");

        } catch (Exception e) {
            System.err.println("Error ensuring Skupper site ConfigMap exists: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Ensures a Skupper connection token request secret exists for the robot.
     * If the secret doesn't exist, it creates one with the skupper.io/type label.
     * Updates skupper state: "Token Request" when created, "Secret Cert Created" when Skupper adds certs.
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
                
                // Check if Skupper has added certificates to the secret
                if (existingSecret.getData() != null && !existingSecret.getData().isEmpty()) {
                    // Secret has data - Skupper has added the certificates
                    robotStatusController.setRobotSkupperState(robotName, "Secret Cert Created");
                } else {
                    // Secret exists but no data yet - still waiting for Skupper
                    robotStatusController.setRobotSkupperState(robotName, "Token Request");
                }
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
            
            // Update skupper state to Token Request
            robotStatusController.setRobotSkupperState(robotName, "Token Request");

        } catch (Exception e) {
            System.err.println("Error ensuring secret exists for robot '" + robotName + "': " + e.getMessage());
            e.printStackTrace();
        }
    }

    @GET
    @Path("/getToken")
    @Operation(summary = "Returns the complete YAML of the secret for a robot from the OpenShift cluster, including labels and annotations.")
    @Produces("application/x-yaml")
    public Response getToken(
            @Parameter(description = "Robot name (used as secret name in the robot namespace)", required = true) 
            @RestQuery("robot_name") String robotName) {
        
        if (robotName == null || robotName.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("robot_name query parameter is required")
                    .build();
        }

        System.out.println("Fetching secret '" + robotName + "' in namespace '" + ROBOT_NAMESPACE + "'");

        try {
            // Fetch the secret from OpenShift
            Secret secret = openShiftClient.secrets()
                    .inNamespace(ROBOT_NAMESPACE)
                    .withName(robotName)
                    .get();

            if (secret == null) {
                System.err.println("Secret not found for robot: " + robotName);
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Secret not found for robot: " + robotName)
                        .build();
            }

            // Convert the secret to YAML format (includes all metadata, labels, annotations, and data)
            String secretYaml = Serialization.asYaml(secret);

            System.out.println("Successfully retrieved secret YAML for robot: " + robotName);
            
            // Update skupper state to Cert retrieved
            robotStatusController.setRobotSkupperState(robotName, "Cert retrieved");
            
            return Response.ok(secretYaml).build();

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
