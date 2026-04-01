package org.redhat;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.openshift.client.OpenShiftClient;

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
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import jakarta.annotation.PostConstruct;
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
        private static final int ROBOT_APP_HTTP_PORT = 80;
        private static final int ROBOT_APP_REQUEST_TIMEOUT_SEC = 15;

        private static final String STARTER_APP_LABEL = "starterapp-python";
        private static final String STARTER_APP_LABEL_KEY = "app";
        private static final String STARTER_APP_NAMESPACE = "robot-app";
        private static final int DEFAULT_LOG_LINES = 200;
        private static final String GITOPS_NAMESPACE = "openshift-gitops";
        private static final String ARGOCD_CLUSTER_SECRET_PREFIX = "cluster-";

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

        @Inject
        OpenShiftClient openShiftClient;

        @Inject
        Vertx vertx;

        private WebClient robotAppWebClient;

        @PostConstruct
        void initRobotAppWebClient() {
                robotAppWebClient = WebClient.create(vertx);
        }

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
                System.out.println("[Robot] status key=" + sanitizedKey);
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
                        System.out.println("[Robot] remote_status " + sanitizedKey + " failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
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
                System.out.println("[Robot] distance key=" + sanitizedKey);

                if (sanitizedKey == null || !robotStatusController.robotExists(sanitizedKey))
                        return "Robot Not Registered";

                URI url = new URI(getRobotURLFromConfigMap(sanitizedKey));

                if (robotStatusController.updateRobot(sanitizedKey, "distance"))
                        return "Robot Disconnected";

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(new URI(url + "/distance"))
                                .GET()
                                .build();
                HttpResponse<String> response = HttpClient
                                .newBuilder().build().send(request, BodyHandlers.ofString());

                robotStatusController.setRobotStatus(sanitizedKey, true);

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
                System.out.println("[Robot] forward key=" + sanitizedKey + " cm=" + lengthInCm);

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
                System.out.println("[Robot] backward key=" + sanitizedKey + " cm=" + lengthInCm);

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
                System.out.println("[Robot] left key=" + sanitizedKey + " deg=" + degrees);

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
                System.out.println("[Robot] right key=" + sanitizedKey + " deg=" + degrees);

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
                System.out.println("[Robot] disconnect id=" + sanitizedId);

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
                        throws InterruptedException, ExecutionException, TimeoutException {

                String sanitizedId = sanitizeUserKey(robotShortId);
                System.out.println("runapp called for robotId- > " + sanitizedId);

                if (sanitizedId == null) return "Invalid robot ID";
                Robot robot = robotStatusController.findRobotByShortName(sanitizedId);
                if (robot == null) return "Robot Not Found";
                String robotId = robot.getName();

                System.out.println("runapp resolving to robotId -> " + robotId);

                int port = getRobotAppPort();
                String connectHost = getRobotAppConnectHost(robotId);
                String hostHeader = "starterapp-python-robot-app.apps." + robotId;
                io.vertx.ext.web.client.HttpResponse<io.vertx.core.buffer.Buffer> response = robotAppWebClient
                                .post(port, connectHost, "/run")
                                .putHeader("Host", hostHeader)
                                .send()
                                .toCompletionStage()
                                .toCompletableFuture()
                                .get(ROBOT_APP_REQUEST_TIMEOUT_SEC, TimeUnit.SECONDS);

                return response.bodyAsString();
        }

        @POST
        @Path("/stopapp/{robotId}")
        @Operation(summary = "Stop the application on the robot")
        @Produces("text/html")
        public String stopapp(
                        @Parameter(description = "The token of the robot", required = true) @RestPath("robotId") String robotShortId)
                        throws InterruptedException, ExecutionException, TimeoutException {

                String sanitizedId = sanitizeUserKey(robotShortId);
                System.out.println("stopapp called for robotId- > " + sanitizedId);

                if (sanitizedId == null) return "Invalid robot ID";
                Robot robot = robotStatusController.findRobotByShortName(sanitizedId);
                if (robot == null) return "Robot Not Found";
                String robotId = robot.getName();

                System.out.println("stopapp resolving to robotId -> " + robotId);

                int port = getRobotAppPort();
                String connectHost = getRobotAppConnectHost(robotId);
                String hostHeader = "starterapp-python-robot-app.apps." + robotId;
                io.vertx.ext.web.client.HttpResponse<io.vertx.core.buffer.Buffer> response = robotAppWebClient
                                .post(port, connectHost, "/stop")
                                .putHeader("Host", hostHeader)
                                .send()
                                .toCompletionStage()
                                .toCompletableFuture()
                                .get(ROBOT_APP_REQUEST_TIMEOUT_SEC, TimeUnit.SECONDS);

                return response.bodyAsString();
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
                System.out.println("[Robot] led_on key=" + sanitizedKey + " color=" + color);

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
                System.out.println("[Robot] led_off key=" + sanitizedKey);

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
                        System.out.println("[Robot] camera " + sanitizedKey + " failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
                        return "Connection Error";
                }
        }

        @GET
        @Path("/logs")
        @Operation(summary = "Get pod logs from the starter-app on the robot's MicroShift cluster")
        @Produces("text/plain")
        public String logs(
                        @Parameter(description = "The token of the robot", required = true) @RestQuery(API_TOKEN) String userKey,
                        @Parameter(description = "Number of log lines (default 200)", required = false) @RestQuery("lines") Integer lines) {

                String sanitizedKey = sanitizeUserKey(userKey);
                if (sanitizedKey == null) {
                        System.out.println("[Logs] key=null -> Robot Not Registered");
                        return "Robot Not Registered";
                }
                System.out.println("[Logs] key=" + sanitizedKey);

                Robot robot = robotStatusController.findRobotByShortName(sanitizedKey);
                if (robot == null)
                        robot = robotStatusController.getRobotList().stream()
                                        .filter(r -> sanitizedKey.equals(r.getName()))
                                        .findFirst()
                                        .orElse(null);
                if (robot == null) {
                        System.out.println("[Logs] robot not found");
                        return "Robot Not Registered";
                }

                String robotName = robot.getName();
                String caCert = robot.getCaCert();
                String clientCert = robot.getClientCert();
                String clientKey = robot.getClientKey();

                if ((caCert == null || caCert.isBlank() || clientCert == null || clientCert.isBlank() || clientKey == null || clientKey.isBlank())
                                && LaunchMode.current() != LaunchMode.TEST && LaunchMode.current() != LaunchMode.DEVELOPMENT) {
                        CertKey fromSecret = loadCredentialsFromArgoCDSecret(robotName);
                        if (fromSecret != null) {
                                clientCert = fromSecret.cert();
                                clientKey = fromSecret.key();
                                caCert = null;
                                System.out.println("[Logs] " + robotName + " creds from ArgoCD secret");
                        }
                }

                if (clientCert == null || clientCert.isBlank() || clientKey == null || clientKey.isBlank()) {
                        System.out.println("[Logs] " + robotName + " no creds");
                        return "Credentials not set. Call /control/setRobotCreds for this robot to view MicroShift pod logs.";
                }

                int tailLines = (lines != null && lines > 0) ? Math.min(lines, 1000) : DEFAULT_LOG_LINES;
                String masterUrl = "https://" + robotName + ".robot.svc.cluster.local.:6443";

                System.out.println("[Logs] " + robotName + " connecting to " + masterUrl
                        + " caCert=" + (caCert != null && !caCert.isBlank() ? caCert.length() + "chars" : "none")
                        + " clientCert=" + (clientCert != null ? clientCert.length() + "chars" : "null")
                        + " clientKey=" + (clientKey != null ? clientKey.length() + "chars" : "null"));

                try {
                        ConfigBuilder configBuilder = new ConfigBuilder()
                                        .withMasterUrl(masterUrl)
                                        .withClientCertData(clientCert)
                                        .withClientKeyData(clientKey)
                                        .withTrustCerts(true)
                                        .withDisableHostnameVerification(true)
                                        .withRequestTimeout(15_000)
                                        .withConnectionTimeout(10_000);
                        if (caCert != null && !caCert.isBlank()) {
                                configBuilder.withCaCertData(caCert);
                        }
                        Config config = configBuilder.build();

                        try (KubernetesClient microShiftClient = new KubernetesClientBuilder().withConfig(config).build()) {
                                PodList pods = listPodsWithFallback(microShiftClient, robotName);
                                if (pods == null || pods.getItems() == null || pods.getItems().isEmpty()) {
                                        System.out.println("[Logs] " + robotName + " no pod app=" + STARTER_APP_LABEL);
                                        return "No pod with label app=" + STARTER_APP_LABEL + " found on MicroShift at " + masterUrl;
                                }

                                var pod = pods.getItems().get(0);
                                String namespace = pod.getMetadata().getNamespace();
                                String podName = pod.getMetadata().getName();
                                System.out.println("[Logs] " + robotName + " pod " + namespace + "/" + podName + " lines=" + tailLines);

                                String log = microShiftClient.pods()
                                                .inNamespace(namespace)
                                                .withName(podName)
                                                .tailingLines(tailLines)
                                                .getLog();

                                if (log == null || log.isEmpty()) {
                                        System.out.println("[Logs] " + robotName + " pod " + podName + " empty");
                                        return "No logs available for pod " + podName;
                                }
                                System.out.println("[Logs] " + robotName + " ok " + log.split("\n").length + " lines");
                                return log;
                        }
                } catch (Exception e) {
                        System.out.println("[Logs] " + robotName + " error: " + e.getClass().getName() + " " + e.getMessage());
                        if (e.getCause() != null)
                                System.out.println("[Logs] cause: " + e.getCause().getClass().getName() + " " + e.getCause().getMessage());
                        e.printStackTrace(System.out);
                        return "Error fetching logs: " + e.getMessage();
                }
        }

        /**
         * Tries listing pods in robot-app, then default, then any namespace; returns first non-empty list or null.
         * Note: Fabric8 often reports "name: [null]" in list failures because list() has no single resource name.
         */
        private PodList listPodsWithFallback(KubernetesClient client, String robotName) {
                for (String ns : new String[] { STARTER_APP_NAMESPACE, "default" }) {
                        try {
                                System.out.println("[Logs] " + robotName + " listing pods ns=" + ns + " label=" + STARTER_APP_LABEL_KEY + "=" + STARTER_APP_LABEL);
                                PodList pods = client.pods()
                                                .inNamespace(ns)
                                                .withLabel(STARTER_APP_LABEL_KEY, STARTER_APP_LABEL)
                                                .list();
                                int count = (pods != null && pods.getItems() != null) ? pods.getItems().size() : 0;
                                System.out.println("[Logs] " + robotName + " ns=" + ns + " found " + count + " pods");
                                if (count > 0)
                                        return pods;
                        } catch (Exception e) {
                                System.out.println("[Logs] " + robotName + " list ns=" + ns + " failed: " + e.getClass().getName() + " " + e.getMessage());
                                if (e.getCause() != null)
                                        System.out.println("[Logs] cause: " + e.getCause().getClass().getName() + " " + e.getCause().getMessage());
                                e.printStackTrace(System.out);
                        }
                }
                try {
                        System.out.println("[Logs] " + robotName + " listing pods inAnyNamespace label=" + STARTER_APP_LABEL_KEY + "=" + STARTER_APP_LABEL);
                        PodList pods = client.pods()
                                        .inAnyNamespace()
                                        .withLabel(STARTER_APP_LABEL_KEY, STARTER_APP_LABEL)
                                        .list();
                        int count = (pods != null && pods.getItems() != null) ? pods.getItems().size() : 0;
                        System.out.println("[Logs] " + robotName + " inAnyNamespace found " + count + " pods");
                        if (count > 0)
                                return pods;
                } catch (Exception e) {
                        System.out.println("[Logs] " + robotName + " list inAnyNamespace failed: " + e.getClass().getName() + " " + e.getMessage());
                        if (e.getCause() != null)
                                System.out.println("[Logs] cause: " + e.getCause().getClass().getName() + " " + e.getCause().getMessage());
                        e.printStackTrace(System.out);
                }
                return null;
        }

        private record CertKey(String cert, String key) {
        }

        /**
         * Loads client cert and key from the ArgoCD cluster secret (openshift-gitops/cluster-{robotName})
         * if it exists. The secret's "config" holds JSON with tlsClientConfig.certData and keyData.
         * Returns null if secret missing or config invalid. Used when in-memory credentials were not set this session.
         */
        private CertKey loadCredentialsFromArgoCDSecret(String robotName) {
                if (LaunchMode.current() == LaunchMode.TEST || LaunchMode.current() == LaunchMode.DEVELOPMENT) {
                        return null;
                }
                try {
                        String secretName = ARGOCD_CLUSTER_SECRET_PREFIX + robotName;
                        Secret secret = openShiftClient.secrets()
                                        .inNamespace(GITOPS_NAMESPACE)
                                        .withName(secretName)
                                        .get();
                        if (secret == null || secret.getData() == null)
                                return null;

                        Object configRaw = secret.getData().get("config");
                        if (configRaw == null)
                                return null;
                        byte[] configBytes = configRaw instanceof byte[]
                                        ? (byte[]) configRaw
                                        : Base64.getDecoder().decode(configRaw.toString());

                        String configJson = new String(configBytes, StandardCharsets.UTF_8);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> config = new ObjectMapper().readValue(configJson, Map.class);
                        Object tls = config != null ? config.get("tlsClientConfig") : null;
                        if (!(tls instanceof Map))
                                return null;
                        @SuppressWarnings("unchecked")
                        Map<String, String> tlsConfig = (Map<String, String>) tls;
                        String certData = tlsConfig.get("certData");
                        String keyData = tlsConfig.get("keyData");
                        if (certData == null || keyData == null)
                                return null;

                        certData = certData.replace("\\n", "\n");
                        keyData = keyData.replace("\\n", "\n");
                        return new CertKey(certData, keyData);
                } catch (Exception e) {
                        System.out.println("[Logs] ArgoCD secret " + robotName + " failed: " + e.getMessage());
                        return null;
                }
        }

        private String getRobotURLFromConfigMap(String token) {

                if (mockServerEndpoint != null && LaunchMode.current().equals(LaunchMode.TEST))
                        return mockServerEndpoint;

                String apiTokenMap = System.getenv().getOrDefault("MAP", robotMap);
                ObjectReader reader = new ObjectMapper().readerFor(Map.class);
                String hostName = null;

                try {
                        Map<String, String> map = reader.readValue(apiTokenMap);
                        hostName = map.get(token);
                        if (hostName == null)
                                hostName = addHostExtension(token);
                } catch (IOException e) {
                        System.out.println("[Robot] config parse error: " + e.getMessage());
                }

                return "http://" + hostName + ":5000";
        }

        private String addHostExtension(String host) {
                if (host.contains("."))
                        return host;
                return host + ".robot.svc.cluster.local.";

        }

        /**
         * In test mode with mock server, returns host and port from mock server URL so runapp/stopapp hit the mock.
         * Otherwise returns production host (robotId.robot.svc.cluster.local.) and port 80.
         */
        private String getRobotAppConnectHost(String robotId) {
                if (LaunchMode.current() == LaunchMode.TEST && mockServerEndpoint != null && !mockServerEndpoint.isBlank()) {
                        try {
                                URI uri = new URI(mockServerEndpoint);
                                String host = uri.getHost();
                                return host != null ? host : robotId + ".robot.svc.cluster.local.";
                        } catch (URISyntaxException e) {
                                return robotId + ".robot.svc.cluster.local.";
                        }
                }
                return robotId + ".robot.svc.cluster.local.";
        }

        private int getRobotAppPort() {
                if (LaunchMode.current() == LaunchMode.TEST && mockServerEndpoint != null && !mockServerEndpoint.isBlank()) {
                        try {
                                URI uri = new URI(mockServerEndpoint);
                                int port = uri.getPort();
                                if (port > 0) return port;
                        } catch (URISyntaxException e) {
                                // fall through
                        }
                }
                return ROBOT_APP_HTTP_PORT;
        }
}
