/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud_opentelemetry.detector_resources_support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.cloud.opentelemetry.detection.AttributeKeys;
import com.google.cloud.opentelemetry.detection.DetectedPlatform;
import com.google.cloud.opentelemetry.detection.GCPPlatformDetector;
import com.google.cloud.opentelemetry.detection.GCPPlatformDetector.SupportedPlatform;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class Detector_resources_supportTest {
    private static final String METADATA_PREFIX = "/computeMetadata/v1/";

    @Test
    void attributeKeysExposeExpectedSemanticNamesAndAliases() {
        assertThat(AttributeKeys.GCE_AVAILABILITY_ZONE).isEqualTo("availability_zone");
        assertThat(AttributeKeys.GCE_CLOUD_REGION).isEqualTo("cloud_region");
        assertThat(AttributeKeys.GCE_INSTANCE_ID).isEqualTo("instance_id");
        assertThat(AttributeKeys.GCE_INSTANCE_NAME).isEqualTo("instance_name");
        assertThat(AttributeKeys.GCE_MACHINE_TYPE).isEqualTo("machine_type");
        assertThat(AttributeKeys.GCE_INSTANCE_HOSTNAME).isEqualTo("instance_hostname");

        assertThat(AttributeKeys.GKE_CLUSTER_NAME).isEqualTo("gke_cluster_name");
        assertThat(AttributeKeys.GKE_CLUSTER_LOCATION_TYPE).isEqualTo("gke_cluster_location_type");
        assertThat(AttributeKeys.GKE_CLUSTER_LOCATION).isEqualTo("gke_cluster_location");
        assertThat(AttributeKeys.GKE_HOST_ID).isEqualTo(AttributeKeys.GCE_INSTANCE_ID);
        assertThat(AttributeKeys.GKE_LOCATION_TYPE_ZONE).isEqualTo("ZONE");
        assertThat(AttributeKeys.GKE_LOCATION_TYPE_REGION).isEqualTo("REGION");

        assertThat(AttributeKeys.GAE_MODULE_NAME).isEqualTo("gae_module_name");
        assertThat(AttributeKeys.GAE_APP_VERSION).isEqualTo("gae_app_version");
        assertThat(AttributeKeys.GAE_INSTANCE_ID).isEqualTo(AttributeKeys.GCE_INSTANCE_ID);
        assertThat(AttributeKeys.GAE_AVAILABILITY_ZONE).isEqualTo(AttributeKeys.GCE_AVAILABILITY_ZONE);
        assertThat(AttributeKeys.GAE_CLOUD_REGION).isEqualTo(AttributeKeys.GCE_CLOUD_REGION);

        assertThat(AttributeKeys.SERVERLESS_COMPUTE_NAME).isEqualTo("serverless_compute_name");
        assertThat(AttributeKeys.SERVERLESS_COMPUTE_REVISION).isEqualTo("serverless_compute_revision");
        assertThat(AttributeKeys.SERVERLESS_COMPUTE_AVAILABILITY_ZONE).isEqualTo(AttributeKeys.GCE_AVAILABILITY_ZONE);
        assertThat(AttributeKeys.SERVERLESS_COMPUTE_CLOUD_REGION).isEqualTo(AttributeKeys.GCE_CLOUD_REGION);
        assertThat(AttributeKeys.SERVERLESS_COMPUTE_INSTANCE_ID).isEqualTo(AttributeKeys.GCE_INSTANCE_ID);
    }

    @Test
    void supportedPlatformEnumHasStablePublicConstants() {
        assertThat(SupportedPlatform.values())
                .containsExactly(
                        SupportedPlatform.GOOGLE_COMPUTE_ENGINE,
                        SupportedPlatform.GOOGLE_KUBERNETES_ENGINE,
                        SupportedPlatform.GOOGLE_APP_ENGINE,
                        SupportedPlatform.GOOGLE_CLOUD_RUN,
                        SupportedPlatform.GOOGLE_CLOUD_FUNCTIONS,
                        SupportedPlatform.UNKNOWN_PLATFORM);
        assertThat(SupportedPlatform.valueOf("GOOGLE_COMPUTE_ENGINE"))
                .isEqualTo(SupportedPlatform.GOOGLE_COMPUTE_ENGINE);
        assertThat(SupportedPlatform.valueOf("GOOGLE_KUBERNETES_ENGINE"))
                .isEqualTo(SupportedPlatform.GOOGLE_KUBERNETES_ENGINE);
        assertThat(SupportedPlatform.valueOf("GOOGLE_APP_ENGINE"))
                .isEqualTo(SupportedPlatform.GOOGLE_APP_ENGINE);
        assertThat(SupportedPlatform.valueOf("GOOGLE_CLOUD_RUN"))
                .isEqualTo(SupportedPlatform.GOOGLE_CLOUD_RUN);
        assertThat(SupportedPlatform.valueOf("GOOGLE_CLOUD_FUNCTIONS"))
                .isEqualTo(SupportedPlatform.GOOGLE_CLOUD_FUNCTIONS);
        assertThat(SupportedPlatform.valueOf("UNKNOWN_PLATFORM"))
                .isEqualTo(SupportedPlatform.UNKNOWN_PLATFORM);
    }

    @Test
    @Timeout(60)
    void defaultDetectorUsesMetadataServiceAndCurrentEnvironmentToBuildDetectedPlatform() throws Exception {
        try (MetadataProxy metadataProxy = MetadataProxy.start()) {
            withMetadataProxy(metadataProxy, () -> {
                metadataProxy.replaceRoutes(Map.of());

                DetectedPlatform unknownPlatform = GCPPlatformDetector.DEFAULT_INSTANCE.detectPlatform();
                assertThat(unknownPlatform.getSupportedPlatform()).isEqualTo(SupportedPlatform.UNKNOWN_PLATFORM);
                assertThat(unknownPlatform.getProjectId()).isEmpty();
                assertThat(unknownPlatform.getAttributes()).isEmpty();
                assertUnmodifiable(unknownPlatform.getAttributes());

                metadataProxy.replaceRoutes(googleCloudMetadataRoutes());

                DetectedPlatform detectedPlatform = GCPPlatformDetector.DEFAULT_INSTANCE.detectPlatform();
                assertThat(detectedPlatform.getProjectId()).isEqualTo("test-project");
                assertThat(detectedPlatform.getSupportedPlatform()).isEqualTo(expectedPlatformFromEnvironment());
                assertAttributesForCurrentEnvironment(detectedPlatform.getAttributes());
                assertUnmodifiable(detectedPlatform.getAttributes());
            });
        }
    }

    private static void assertAttributesForCurrentEnvironment(Map<String, String> attributes) {
        SupportedPlatform expectedPlatform = expectedPlatformFromEnvironment();
        if (expectedPlatform == SupportedPlatform.GOOGLE_KUBERNETES_ENGINE) {
            assertThat(attributes)
                    .hasSize(4)
                    .containsEntry(AttributeKeys.GKE_CLUSTER_NAME, "cluster-one")
                    .containsEntry(AttributeKeys.GKE_CLUSTER_LOCATION, "us-central1-a")
                    .containsEntry(AttributeKeys.GKE_CLUSTER_LOCATION_TYPE, AttributeKeys.GKE_LOCATION_TYPE_ZONE)
                    .containsEntry(AttributeKeys.GKE_HOST_ID, "9876543210");
        } else if (expectedPlatform == SupportedPlatform.GOOGLE_CLOUD_RUN
                || expectedPlatform == SupportedPlatform.GOOGLE_CLOUD_FUNCTIONS) {
            assertThat(attributes)
                    .hasSize(5)
                    .containsEntry(AttributeKeys.SERVERLESS_COMPUTE_NAME, System.getenv("K_SERVICE"))
                    .containsEntry(AttributeKeys.SERVERLESS_COMPUTE_REVISION, System.getenv("K_REVISION"))
                    .containsEntry(AttributeKeys.SERVERLESS_COMPUTE_AVAILABILITY_ZONE, "us-central1-a")
                    .containsEntry(AttributeKeys.SERVERLESS_COMPUTE_CLOUD_REGION, "us-central1")
                    .containsEntry(AttributeKeys.SERVERLESS_COMPUTE_INSTANCE_ID, "9876543210");
        } else if (expectedPlatform == SupportedPlatform.GOOGLE_APP_ENGINE) {
            assertThat(attributes)
                    .hasSize(5)
                    .containsEntry(AttributeKeys.GAE_MODULE_NAME, System.getenv("GAE_SERVICE"))
                    .containsEntry(AttributeKeys.GAE_APP_VERSION, System.getenv("GAE_VERSION"))
                    .containsEntry(AttributeKeys.GAE_INSTANCE_ID, System.getenv("GAE_INSTANCE"))
                    .containsEntry(AttributeKeys.GAE_AVAILABILITY_ZONE, "us-central1-a")
                    .containsEntry(AttributeKeys.GAE_CLOUD_REGION, "us-central1");
        } else {
            assertThat(attributes)
                    .hasSize(6)
                    .containsEntry(AttributeKeys.GCE_AVAILABILITY_ZONE, "us-central1-a")
                    .containsEntry(AttributeKeys.GCE_CLOUD_REGION, "us-central1")
                    .containsEntry(AttributeKeys.GCE_INSTANCE_ID, "9876543210")
                    .containsEntry(AttributeKeys.GCE_INSTANCE_NAME, "test-instance")
                    .containsEntry(AttributeKeys.GCE_MACHINE_TYPE, "e2-standard-4")
                    .containsEntry(AttributeKeys.GCE_INSTANCE_HOSTNAME, "test-instance.c.project.internal");
        }
    }

    private static SupportedPlatform expectedPlatformFromEnvironment() {
        if (System.getenv("KUBERNETES_SERVICE_HOST") != null) {
            return SupportedPlatform.GOOGLE_KUBERNETES_ENGINE;
        }
        if (System.getenv("K_CONFIGURATION") != null && System.getenv("FUNCTION_TARGET") == null) {
            return SupportedPlatform.GOOGLE_CLOUD_RUN;
        }
        if (System.getenv("FUNCTION_TARGET") != null) {
            return SupportedPlatform.GOOGLE_CLOUD_FUNCTIONS;
        }
        if (System.getenv("GAE_SERVICE") != null) {
            return SupportedPlatform.GOOGLE_APP_ENGINE;
        }
        return SupportedPlatform.GOOGLE_COMPUTE_ENGINE;
    }

    private static void assertUnmodifiable(Map<String, String> attributes) {
        assertThatThrownBy(() -> attributes.put("new_attribute", "new_value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static Map<String, String> googleCloudMetadataRoutes() {
        Map<String, String> routes = new HashMap<>();
        routes.put("project/project-id", "test-project");
        routes.put("instance/zone", "projects/123/zones/us-central1-a");
        routes.put("instance/region", "projects/123/regions/us-central1");
        routes.put("instance/id", "9876543210");
        routes.put("instance/name", "test-instance");
        routes.put("instance/hostname", "test-instance.c.project.internal");
        routes.put("instance/machine-type", "projects/123/machineTypes/e2-standard-4");
        routes.put("instance/attributes/cluster-name", "cluster-one");
        routes.put("instance/attributes/cluster-location", "us-central1-a");
        return routes;
    }

    private static void withMetadataProxy(MetadataProxy metadataProxy, ThrowingRunnable runnable) throws Exception {
        String originalProxyHost = System.getProperty("http.proxyHost");
        String originalProxyPort = System.getProperty("http.proxyPort");
        String originalNonProxyHosts = System.getProperty("http.nonProxyHosts");
        System.setProperty("http.proxyHost", metadataProxy.host());
        System.setProperty("http.proxyPort", Integer.toString(metadataProxy.port()));
        System.setProperty("http.nonProxyHosts", "");
        try {
            runnable.run();
        } finally {
            restoreProperty("http.proxyHost", originalProxyHost);
            restoreProperty("http.proxyPort", originalProxyPort);
            restoreProperty("http.nonProxyHosts", originalNonProxyHosts);
        }
    }

    private static void restoreProperty(String propertyName, String originalValue) {
        if (originalValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, originalValue);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class MetadataProxy implements AutoCloseable {
        private static final Duration ACCEPT_TIMEOUT = Duration.ofMillis(200);
        private static final Duration SOCKET_TIMEOUT = Duration.ofSeconds(5);

        private final ServerSocket serverSocket;
        private final ExecutorService executorService;
        private volatile Map<String, String> routes;

        private MetadataProxy(ServerSocket serverSocket, ExecutorService executorService) {
            this.serverSocket = serverSocket;
            this.executorService = executorService;
            this.routes = Map.of();
        }

        static MetadataProxy start() throws IOException {
            ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            serverSocket.setSoTimeout((int) ACCEPT_TIMEOUT.toMillis());
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            MetadataProxy metadataProxy = new MetadataProxy(serverSocket, executorService);
            executorService.submit(metadataProxy::serve);
            return metadataProxy;
        }

        String host() {
            return serverSocket.getInetAddress().getHostAddress();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void replaceRoutes(Map<String, String> routes) {
            this.routes = Map.copyOf(routes);
        }

        private void serve() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    handle(socket);
                } catch (SocketTimeoutException ignored) {
                    // Check whether the server socket was closed and continue accepting otherwise.
                } catch (SocketException socketException) {
                    if (!serverSocket.isClosed()) {
                        throw new IllegalStateException("Metadata proxy socket failed", socketException);
                    }
                } catch (IOException ioException) {
                    throw new IllegalStateException("Metadata proxy failed", ioException);
                }
            }
        }

        private void handle(Socket socket) throws IOException {
            try (socket) {
                socket.setSoTimeout((int) SOCKET_TIMEOUT.toMillis());
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
                String requestLine = reader.readLine();
                if (requestLine == null || requestLine.isEmpty()) {
                    return;
                }
                while (true) {
                    String header = reader.readLine();
                    if (header == null || header.isEmpty()) {
                        break;
                    }
                }
                writeResponse(socket.getOutputStream(), metadataKey(requestLine));
            }
        }

        private void writeResponse(OutputStream outputStream, String metadataKey) throws IOException {
            String body = routes.get(metadataKey);
            int statusCode = body == null ? 404 : 200;
            String reasonPhrase = body == null ? "Not Found" : "OK";
            byte[] bodyBytes = (body == null ? "not found" : body).getBytes(StandardCharsets.UTF_8);
            String metadataFlavorHeader = body == null ? "" : "Metadata-Flavor: Google\r\n";
            String response = "HTTP/1.1 " + statusCode + " " + reasonPhrase + "\r\n"
                    + "Content-Type: text/plain; charset=UTF-8\r\n"
                    + "Content-Length: " + bodyBytes.length + "\r\n"
                    + metadataFlavorHeader
                    + "Connection: close\r\n"
                    + "\r\n";
            outputStream.write(response.getBytes(StandardCharsets.ISO_8859_1));
            outputStream.write(bodyBytes);
            outputStream.flush();
        }

        private static String metadataKey(String requestLine) {
            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 2) {
                return "";
            }
            String requestTarget = requestParts[1];
            String path = requestTarget.startsWith("http://") || requestTarget.startsWith("https://")
                    ? URI.create(requestTarget).getPath()
                    : requestTarget;
            if (!path.startsWith(METADATA_PREFIX)) {
                return "";
            }
            return path.substring(METADATA_PREFIX.length());
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            executorService.shutdown();
            if (!executorService.awaitTermination(SOCKET_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        }
    }
}
