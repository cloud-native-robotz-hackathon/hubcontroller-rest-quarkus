package org.redhat;

import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;

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

    // Namespace where robot connection token request secrets are created
    private static final String ROBOT_NAMESPACE = "robot";

    // Skupper label for connection token request
    private static final String SKUPPER_TYPE_LABEL = "skupper.io/type";
    private static final String CONNECTION_TOKEN_REQUEST = "connection-token-request";

    // Label for storing robot-specific UUID
    private static final String ROBOT_UUID_LABEL = "robot.hackathon/uuid";

    // Certificate key that Skupper writes to the secret
    private static final String SKUPPER_CA_CRT_KEY = "ca.crt";

    // Skupper site ConfigMap name
    private static final String SKUPPER_SITE_CONFIGMAP = "skupper-site";

    // Skupper site controller namespace
    private static final String OPENSHIFT_OPERATORS_NAMESPACE = "openshift-operators";

    // In-memory cache for robot UUIDs (used in test/dev mode when OpenShift is not available)
    private final java.util.Map<String, String> robotUuidCache = new java.util.concurrent.ConcurrentHashMap<>();

    @Inject
    RobotStatusController robotStatusController;

    @Inject
    OpenShiftClient openShiftClient;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "1.0.0-SNAPSHOT")
    String appVersion;

    @ConfigProperty(name = "app.build.timestamp", defaultValue = "unknown")
    String buildTimestamp;

    /**
     * On application startup:
     * 1. Restart the skupper-site-controller pod
     * 2. Scan for existing robot secrets and register them
     * Skipped in test and dev modes.
     */
    void onStart(@Observes StartupEvent ev) {
        // Skip OpenShift operations in test and dev modes
        if (LaunchMode.current() == LaunchMode.TEST || LaunchMode.current() == LaunchMode.DEVELOPMENT) {
            System.out.println("Skipping startup OpenShift operations in " + LaunchMode.current() + " mode");
            return;
        }
        restartSkupperSiteController();
        registerExistingRobots();
    }

    /**
     * Scans the robot namespace for existing robot secrets and registers them.
     * Looks for secrets with the robot.hackathon/uuid label (our robot secrets).
     */
    private void registerExistingRobots() {
        try {
            System.out.println("Scanning for existing robot secrets in namespace '" + ROBOT_NAMESPACE + "'...");

            // Find all secrets with the robot UUID label (this identifies our robot secrets)
            var secrets = openShiftClient.secrets()
                    .inNamespace(ROBOT_NAMESPACE)
                    .withLabel(ROBOT_UUID_LABEL)
                    .list()
                    .getItems();

            if (secrets == null || secrets.isEmpty()) {
                System.out.println("No existing robot secrets found");
                return;
            }

            System.out.println("Found " + secrets.size() + " existing robot secret(s)");

            for (Secret secret : secrets) {
                String robotName = secret.getMetadata().getName();
                
                // Register the robot
                boolean registered = robotStatusController.registerRobot(robotName);
                if (registered) {
                    System.out.println("Registered existing robot: " + robotName);
                } else {
                    System.out.println("Robot already registered: " + robotName);
                }

                // Update skupper state based on whether certificate exists
                if (secret.getData() != null && secret.getData().containsKey(SKUPPER_CA_CRT_KEY)) {
                    robotStatusController.setRobotSkupperState(robotName, "Secret Cert Created");
                } else {
                    robotStatusController.setRobotSkupperState(robotName, "Token Request");
                }
            }

        } catch (Exception e) {
            System.err.println("Error scanning for existing robot secrets: " + e.getMessage());
            e.printStackTrace();
        }
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
    @Operation(summary = "Returns a robot-specific UUID. Creates a Skupper connection token request secret with the UUID if it doesn't exist.")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getEventId(
            @Parameter(description = "Robot name to register", required = true) 
            @RestQuery("robot_name") String robotName) {
        
        // Sanitize robot name for Kubernetes resource naming
        String sanitizedName = InputSanitizer.sanitizeRobotName(robotName);
        if (sanitizedName == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("robot_name query parameter is required and must contain valid characters")
                    .build();
        }

        // Register robot in the status controller
        robotStatusController.registerRobot(sanitizedName);

        // In test and dev modes, use cached UUIDs without OpenShift operations
        if (LaunchMode.current() == LaunchMode.TEST || LaunchMode.current() == LaunchMode.DEVELOPMENT) {
            String testUuid = robotUuidCache.computeIfAbsent(sanitizedName, k -> UUID.randomUUID().toString());
            System.out.println("Test/Dev mode: Using UUID '" + testUuid + "' for robot '" + sanitizedName + "'");
            return Response.ok(testUuid).build();
        }

        // Ensure Skupper site ConfigMap exists
        ensureSkupperSiteConfigMapExists();

        // Get or create the robot secret and return its UUID
        String robotUuid = getOrCreateRobotSecret(sanitizedName);
        
        if (robotUuid != null) {
            System.out.println("Returning UUID '" + robotUuid + "' for robot '" + sanitizedName + "'");
            return Response.ok(robotUuid).build();
        } else {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to get or create secret for robot: " + sanitizedName)
                    .build();
        }
    }

    @GET
    @Path("/appInfo")
    @Operation(summary = "Returns application version and build/start timestamp")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAppInfo() {
        String json = String.format("{\"version\":\"%s\",\"buildTime\":\"%s\"}", appVersion, buildTimestamp);
        return Response.ok(json).build();
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
            @FormParam("status") String status,
            @Parameter(description = "Detailed error message shown on hover (optional)")
            @FormParam("status_verbose") String statusVerbose) {

        // Sanitize all inputs
        String sanitizedName = InputSanitizer.sanitizeRobotName(robotName);
        if (sanitizedName == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("robot_name form parameter is required and must contain valid characters")
                    .build();
        }

        String sanitizedStatus = InputSanitizer.sanitizeStatus(status);
        if (sanitizedStatus == null || sanitizedStatus.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("status form parameter is required")
                    .build();
        }

        String sanitizedVerbose = InputSanitizer.sanitizeVerbose(statusVerbose);

        System.out.println("Setting init status for robot '" + sanitizedName + "' to: " + sanitizedStatus);

        boolean updated = robotStatusController.setRobotInitStatus(sanitizedName, sanitizedStatus, sanitizedVerbose);
        
        if (updated) {
            return Response.ok("Status updated for robot: " + sanitizedName).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Robot not found: " + sanitizedName)
                    .build();
        }
    }

    @POST
    @Path("/setRobotCreds")
    @Operation(summary = "Stores MicroShift API credentials (CA cert, client cert, client key) for a robot.")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    public Response setRobotCreds(
            @Parameter(description = "Robot name", required = true)
            @FormParam("robot_name") String robotName,
            @Parameter(description = "CA certificate in PEM format", required = true)
            @FormParam("ca_cert") String caCert,
            @Parameter(description = "Client certificate in PEM format", required = true)
            @FormParam("client_cert") String clientCert,
            @Parameter(description = "Client private key in PEM format", required = true)
            @FormParam("client_key") String clientKey) {

        String sanitizedName = InputSanitizer.sanitizeRobotName(robotName);
        if (sanitizedName == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("robot_name form parameter is required and must contain valid characters")
                    .build();
        }

        if (caCert == null || caCert.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("ca_cert form parameter is required")
                    .build();
        }

        if (clientCert == null || clientCert.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("client_cert form parameter is required")
                    .build();
        }

        if (clientKey == null || clientKey.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("client_key form parameter is required")
                    .build();
        }

        System.out.println("Storing MicroShift credentials for robot '" + sanitizedName + "'");

        boolean updated = robotStatusController.setRobotCreds(sanitizedName, caCert, clientCert, clientKey);

        if (updated) {
            return Response.ok("MicroShift credentials stored for robot: " + sanitizedName).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Robot not found: " + sanitizedName)
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
     * Gets the robot UUID from an existing secret or creates a new secret with a new UUID.
     * The UUID is stored in the robot.hackathon/uuid label.
     * Updates skupper state: "Token Request" when created, "Secret Cert Created" when Skupper adds certs.
     * @return the robot UUID, or null if an error occurred
     */
    private String getOrCreateRobotSecret(String robotName) {
        try {
            // Check if secret already exists
            Secret existingSecret = openShiftClient.secrets()
                    .inNamespace(ROBOT_NAMESPACE)
                    .withName(robotName)
                    .get();

            if (existingSecret != null) {
                System.out.println("Secret '" + robotName + "' already exists in namespace '" + ROBOT_NAMESPACE + "'");
                
                // Get the robot UUID from the label
                String robotUuid = existingSecret.getMetadata().getLabels() != null 
                        ? existingSecret.getMetadata().getLabels().get(ROBOT_UUID_LABEL)
                        : null;
                
                if (robotUuid == null || robotUuid.isBlank()) {
                    // Secret exists but has no UUID label - this shouldn't happen, but generate one
                    robotUuid = UUID.randomUUID().toString();
                    System.out.println("Warning: Secret had no UUID label, generated new UUID: " + robotUuid);
                }
                
                // Check if Skupper has added certificates to the secret
                if (existingSecret.getData() != null && existingSecret.getData().containsKey(SKUPPER_CA_CRT_KEY)) {
                    // Secret has ca.crt - Skupper has added the certificates
                    robotStatusController.setRobotSkupperState(robotName, "Secret Cert Created");
                } else {
                    // Secret exists but no cert yet - still waiting for Skupper
                    robotStatusController.setRobotSkupperState(robotName, "Token Request");
                }
                
                return robotUuid;
            }

            // Generate a new UUID for this robot
            String robotUuid = UUID.randomUUID().toString();

            // Create new secret with Skupper connection token request label and robot UUID label
            Secret newSecret = new SecretBuilder()
                    .withNewMetadata()
                        .withName(robotName)
                        .withNamespace(ROBOT_NAMESPACE)
                        .addToLabels(SKUPPER_TYPE_LABEL, CONNECTION_TOKEN_REQUEST)
                        .addToLabels(ROBOT_UUID_LABEL, robotUuid)
                    .endMetadata()
                    .build();

            openShiftClient.secrets()
                    .inNamespace(ROBOT_NAMESPACE)
                    .resource(newSecret)
                    .create();

            System.out.println("Created Skupper connection token request secret '" + robotName + "' with UUID '" + robotUuid + "' in namespace '" + ROBOT_NAMESPACE + "'");
            
            // Update skupper state to Token Request
            robotStatusController.setRobotSkupperState(robotName, "Token Request");
            
            return robotUuid;

        } catch (Exception e) {
            System.err.println("Error getting/creating secret for robot '" + robotName + "': " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @GET
    @Path("/getToken")
    @Operation(summary = "Returns the complete YAML of the secret for a robot. Only returns the secret if Skupper has written the certificate to it.")
    @Produces("application/x-yaml")
    public Response getToken(
            @Parameter(description = "Robot name (used as secret name in the robot namespace)", required = true) 
            @RestQuery("robot_name") String robotName) {
        
        // Sanitize robot name
        String sanitizedName = InputSanitizer.sanitizeRobotName(robotName);
        if (sanitizedName == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("robot_name query parameter is required and must contain valid characters")
                    .build();
        }

        System.out.println("Fetching secret '" + sanitizedName + "' in namespace '" + ROBOT_NAMESPACE + "'");

        try {
            // Fetch the secret from OpenShift
            Secret secret = openShiftClient.secrets()
                    .inNamespace(ROBOT_NAMESPACE)
                    .withName(sanitizedName)
                    .get();

            if (secret == null) {
                System.err.println("Secret not found for robot: " + sanitizedName);
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Secret not found for robot: " + sanitizedName)
                        .build();
            }

            // Check if Skupper has written the certificate to the secret
            if (secret.getData() == null || !secret.getData().containsKey(SKUPPER_CA_CRT_KEY)) {
                System.out.println("Certificate not yet available for robot: " + sanitizedName + " - Skupper has not written to the secret");
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity("Certificate not yet available. Skupper has not written to the secret.")
                        .build();
            }

            // Convert the secret to YAML format (includes all metadata, labels, annotations, and data)
            String secretYaml = Serialization.asYaml(secret);

            System.out.println("Successfully retrieved secret YAML for robot: " + sanitizedName);
            
            // Update skupper state to Cert retrieved
            robotStatusController.setRobotSkupperState(sanitizedName, "Cert retrieved");
            
            return Response.ok(secretYaml).build();

        } catch (Exception e) {
            System.err.println("Error fetching secret for robot '" + sanitizedName + "': " + e.getMessage());
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
