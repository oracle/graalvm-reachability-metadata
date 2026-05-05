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
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Detector_resources_supportTest {
    @Test
    @Order(1)
    void publicAttributeKeysExposeExpectedOpenTelemetryResourceNames() {
        assertThat(AttributeKeys.GCE_AVAILABILITY_ZONE).isEqualTo("availability_zone");
        assertThat(AttributeKeys.GCE_CLOUD_REGION).isEqualTo("cloud_region");
        assertThat(AttributeKeys.GCE_INSTANCE_ID).isEqualTo("instance_id");
        assertThat(AttributeKeys.GCE_INSTANCE_NAME).isEqualTo("instance_name");
        assertThat(AttributeKeys.GCE_MACHINE_TYPE).isEqualTo("machine_type");
        assertThat(AttributeKeys.GCE_INSTANCE_HOSTNAME).isEqualTo("instance_hostname");
        assertThat(AttributeKeys.GKE_CLUSTER_NAME).isEqualTo("gke_cluster_name");
        assertThat(AttributeKeys.GKE_CLUSTER_LOCATION_TYPE).isEqualTo("gke_cluster_location_type");
        assertThat(AttributeKeys.GKE_CLUSTER_LOCATION).isEqualTo("gke_cluster_location");
        assertThat(AttributeKeys.GKE_HOST_ID).isEqualTo("instance_id");
        assertThat(AttributeKeys.GKE_LOCATION_TYPE_ZONE).isEqualTo("ZONE");
        assertThat(AttributeKeys.GKE_LOCATION_TYPE_REGION).isEqualTo("REGION");
        assertThat(AttributeKeys.GAE_MODULE_NAME).isEqualTo("gae_module_name");
        assertThat(AttributeKeys.GAE_APP_VERSION).isEqualTo("gae_app_version");
        assertThat(AttributeKeys.GAE_INSTANCE_ID).isEqualTo("instance_id");
        assertThat(AttributeKeys.GAE_AVAILABILITY_ZONE).isEqualTo("availability_zone");
        assertThat(AttributeKeys.GAE_CLOUD_REGION).isEqualTo("cloud_region");
        assertThat(AttributeKeys.SERVERLESS_COMPUTE_NAME).isEqualTo("serverless_compute_name");
        assertThat(AttributeKeys.SERVERLESS_COMPUTE_REVISION).isEqualTo("serverless_compute_revision");
        assertThat(AttributeKeys.SERVERLESS_COMPUTE_AVAILABILITY_ZONE).isEqualTo("availability_zone");
        assertThat(AttributeKeys.SERVERLESS_COMPUTE_CLOUD_REGION).isEqualTo("cloud_region");
        assertThat(AttributeKeys.SERVERLESS_COMPUTE_INSTANCE_ID).isEqualTo("instance_id");
        assertThat(AttributeKeys.GCR_JOB_EXECUTION_KEY).isEqualTo("gcr_job_execution_key");
        assertThat(AttributeKeys.GCR_JOB_TASK_INDEX).isEqualTo("gcr_job_task_index");
    }

    @Test
    @Order(2)
    void supportedPlatformEnumContainsAllAdvertisedPlatforms() {
        Set<SupportedPlatform> supportedPlatforms = EnumSet.allOf(SupportedPlatform.class);

        assertThat(supportedPlatforms)
                .containsExactly(
                        SupportedPlatform.GOOGLE_COMPUTE_ENGINE,
                        SupportedPlatform.GOOGLE_KUBERNETES_ENGINE,
                        SupportedPlatform.GOOGLE_APP_ENGINE,
                        SupportedPlatform.GOOGLE_CLOUD_RUN,
                        SupportedPlatform.GOOGLE_CLOUD_RUN_JOB,
                        SupportedPlatform.GOOGLE_CLOUD_FUNCTIONS,
                        SupportedPlatform.UNKNOWN_PLATFORM);
        assertThat(SupportedPlatform.valueOf("GOOGLE_COMPUTE_ENGINE"))
                .isEqualTo(SupportedPlatform.GOOGLE_COMPUTE_ENGINE);
    }

    @Test
    @Order(3)
    void detectorRejectsMetadataResponsesWithoutGoogleFlavorHeader() throws Exception {
        ProxySelector originalProxySelector = ProxySelector.getDefault();
        try (MetadataProxy metadataProxy = new MetadataProxy()) {
            ProxySelector.setDefault(metadataProxy.proxySelector(originalProxySelector));
            metadataProxy.respondWithoutMetadataFlavor(Map.of("project/project-id", "project-without-flavor"));

            DetectedPlatform platform = GCPPlatformDetector.DEFAULT_INSTANCE.detectPlatform();

            assertThat(platform.getSupportedPlatform()).isEqualTo(SupportedPlatform.UNKNOWN_PLATFORM);
            assertThat(platform.getProjectId()).isEmpty();
            assertThat(platform.getAttributes()).isEmpty();
            assertThat(metadataProxy.requestHeaders("project/project-id"))
                    .anySatisfy(headers -> assertThat(headers).containsEntry("metadata-flavor", "Google"));
        } finally {
            ProxySelector.setDefault(originalProxySelector);
        }
    }

    @Test
    @Order(4)
    void detectorHandlesMissingAndAvailableMetadataServerResponses() throws Exception {
        ProxySelector originalProxySelector = ProxySelector.getDefault();
        try (MetadataProxy metadataProxy = new MetadataProxy()) {
            ProxySelector.setDefault(metadataProxy.proxySelector(originalProxySelector));

            DetectedPlatform unknownPlatform = GCPPlatformDetector.DEFAULT_INSTANCE.detectPlatform();

            assertThat(unknownPlatform.getSupportedPlatform()).isEqualTo(SupportedPlatform.UNKNOWN_PLATFORM);
            assertThat(unknownPlatform.getProjectId()).isEmpty();
            assertThat(unknownPlatform.getAttributes()).isEmpty();

            metadataProxy.respondWith(
                    Map.ofEntries(
                            Map.entry("project/project-id", "project-one"),
                            Map.entry("instance/zone", "projects/42/zones/us-central1-b"),
                            Map.entry("instance/region", "projects/42/regions/us-central1"),
                            Map.entry("instance/id", "987654321"),
                            Map.entry("instance/name", "instance-a"),
                            Map.entry("instance/hostname", "instance-a.c.project-one.internal"),
                            Map.entry("instance/machine-type", "projects/42/machineTypes/e2-standard-2"),
                            Map.entry("instance/attributes/cluster-name", "cluster-a"),
                            Map.entry("instance/attributes/cluster-location", "us-central1-b")));

            DetectedPlatform platform = GCPPlatformDetector.DEFAULT_INSTANCE.detectPlatform();

            assertThat(platform.getSupportedPlatform()).isEqualTo(expectedPlatformFromEnvironment());
            assertThat(platform.getProjectId()).isEqualTo("project-one");
            assertAttributesForDetectedPlatform(platform);
            assertThat(metadataProxy.requestHeaders("project/project-id"))
                    .anySatisfy(headers -> assertThat(headers).containsEntry("metadata-flavor", "Google"));
        } finally {
            ProxySelector.setDefault(originalProxySelector);
        }
    }

    private static SupportedPlatform expectedPlatformFromEnvironment() {
        Map<String, String> environment = System.getenv();
        if (environment.containsKey("KUBERNETES_SERVICE_HOST")) {
            return SupportedPlatform.GOOGLE_KUBERNETES_ENGINE;
        }
        if (environment.containsKey("K_CONFIGURATION") && !environment.containsKey("FUNCTION_TARGET")) {
            return SupportedPlatform.GOOGLE_CLOUD_RUN;
        }
        if (environment.containsKey("FUNCTION_TARGET")) {
            return SupportedPlatform.GOOGLE_CLOUD_FUNCTIONS;
        }
        if (environment.containsKey("CLOUD_RUN_JOB")) {
            return SupportedPlatform.GOOGLE_CLOUD_RUN_JOB;
        }
        if (environment.containsKey("GAE_SERVICE")) {
            return SupportedPlatform.GOOGLE_APP_ENGINE;
        }
        return SupportedPlatform.GOOGLE_COMPUTE_ENGINE;
    }

    private static void assertAttributesForDetectedPlatform(DetectedPlatform platform) {
        Map<String, String> attributes = platform.getAttributes();
        switch (platform.getSupportedPlatform()) {
            case GOOGLE_COMPUTE_ENGINE:
                assertThat(attributes)
                        .containsEntry(AttributeKeys.GCE_AVAILABILITY_ZONE, "us-central1-b")
                        .containsEntry(AttributeKeys.GCE_CLOUD_REGION, "us-central1")
                        .containsEntry(AttributeKeys.GCE_INSTANCE_ID, "987654321")
                        .containsEntry(AttributeKeys.GCE_INSTANCE_NAME, "instance-a")
                        .containsEntry(AttributeKeys.GCE_INSTANCE_HOSTNAME, "instance-a.c.project-one.internal")
                        .containsEntry(AttributeKeys.GCE_MACHINE_TYPE, "e2-standard-2");
                assertThatThrownBy(() -> attributes.put("another", "value"))
                        .isInstanceOf(UnsupportedOperationException.class);
                break;
            case GOOGLE_KUBERNETES_ENGINE:
                assertThat(attributes)
                        .containsEntry(AttributeKeys.GKE_CLUSTER_NAME, "cluster-a")
                        .containsEntry(AttributeKeys.GKE_CLUSTER_LOCATION, "us-central1-b")
                        .containsEntry(AttributeKeys.GKE_CLUSTER_LOCATION_TYPE, AttributeKeys.GKE_LOCATION_TYPE_ZONE)
                        .containsEntry(AttributeKeys.GKE_HOST_ID, "987654321");
                assertThatThrownBy(() -> attributes.put("another", "value"))
                        .isInstanceOf(UnsupportedOperationException.class);
                break;
            case GOOGLE_APP_ENGINE:
                assertThat(attributes)
                        .containsEntry(AttributeKeys.GAE_MODULE_NAME, System.getenv("GAE_SERVICE"))
                        .containsEntry(AttributeKeys.GAE_APP_VERSION, System.getenv("GAE_VERSION"))
                        .containsEntry(AttributeKeys.GAE_INSTANCE_ID, System.getenv("GAE_INSTANCE"))
                        .containsEntry(AttributeKeys.GAE_AVAILABILITY_ZONE, "us-central1-b")
                        .containsEntry(AttributeKeys.GAE_CLOUD_REGION, "us-central1");
                assertThatThrownBy(() -> attributes.put("another", "value"))
                        .isInstanceOf(UnsupportedOperationException.class);
                break;
            case GOOGLE_CLOUD_RUN:
            case GOOGLE_CLOUD_FUNCTIONS:
                assertThat(attributes)
                        .containsEntry(AttributeKeys.SERVERLESS_COMPUTE_NAME, System.getenv("K_SERVICE"))
                        .containsEntry(AttributeKeys.SERVERLESS_COMPUTE_REVISION, System.getenv("K_REVISION"))
                        .containsEntry(AttributeKeys.SERVERLESS_COMPUTE_AVAILABILITY_ZONE, "us-central1-b")
                        .containsEntry(AttributeKeys.SERVERLESS_COMPUTE_CLOUD_REGION, "us-central1")
                        .containsEntry(AttributeKeys.SERVERLESS_COMPUTE_INSTANCE_ID, "987654321");
                assertThatThrownBy(() -> attributes.put("another", "value"))
                        .isInstanceOf(UnsupportedOperationException.class);
                break;
            case GOOGLE_CLOUD_RUN_JOB:
                assertThat(attributes)
                        .containsEntry(AttributeKeys.SERVERLESS_COMPUTE_NAME, System.getenv("CLOUD_RUN_JOB"))
                        .containsEntry(AttributeKeys.GCR_JOB_EXECUTION_KEY, System.getenv("CLOUD_RUN_EXECUTION"))
                        .containsEntry(AttributeKeys.GCR_JOB_TASK_INDEX, System.getenv("CLOUD_RUN_TASK_INDEX"))
                        .containsEntry(AttributeKeys.SERVERLESS_COMPUTE_CLOUD_REGION, "us-central1")
                        .containsEntry(AttributeKeys.SERVERLESS_COMPUTE_INSTANCE_ID, "987654321");
                break;
            case UNKNOWN_PLATFORM:
                throw new AssertionError("Metadata-backed detection should not produce UNKNOWN_PLATFORM");
            default:
                throw new AssertionError("Unsupported platform: " + platform.getSupportedPlatform());
        }
    }

    private static final class MetadataProxy implements AutoCloseable {
        private static final String METADATA_PREFIX = "/computeMetadata/v1/";

        private final ServerSocket serverSocket;
        private final ExecutorService executor;
        private final Map<String, MetadataResponse> responses = new ConcurrentHashMap<>();
        private final Map<String, Set<Map<String, String>>> requestHeaders = new ConcurrentHashMap<>();

        private MetadataProxy() throws IOException {
            serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            serverSocket.setSoTimeout(200);
            executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "metadata-proxy");
                thread.setDaemon(true);
                return thread;
            });
            executor.submit(this::acceptRequests);
        }

        private ProxySelector proxySelector(ProxySelector fallback) {
            Proxy proxy = new Proxy(
                    Proxy.Type.HTTP,
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort()));
            return new ProxySelector() {
                @Override
                public List<Proxy> select(URI uri) {
                    if ("http".equalsIgnoreCase(uri.getScheme())
                            && "metadata.google.internal".equalsIgnoreCase(uri.getHost())) {
                        return List.of(proxy);
                    }
                    if (fallback != null) {
                        return fallback.select(uri);
                    }
                    return List.of(Proxy.NO_PROXY);
                }

                @Override
                public void connectFailed(URI uri, SocketAddress socketAddress, IOException exception) {
                    if (fallback != null) {
                        fallback.connectFailed(uri, socketAddress, exception);
                    }
                }
            };
        }

        private void respondWith(Map<String, String> newResponses) {
            responses.clear();
            newResponses.forEach((key, body) -> responses.put(key, new MetadataResponse(body, true)));
        }

        private void respondWithoutMetadataFlavor(Map<String, String> newResponses) {
            responses.clear();
            newResponses.forEach((key, body) -> responses.put(key, new MetadataResponse(body, false)));
        }

        private Set<Map<String, String>> requestHeaders(String key) {
            return requestHeaders.getOrDefault(key, Set.of());
        }

        private void acceptRequests() {
            while (!serverSocket.isClosed()) {
                try {
                    handle(serverSocket.accept());
                } catch (SocketTimeoutException ignored) {
                    // Continue so close() can stop the loop promptly.
                } catch (IOException ignored) {
                    if (!serverSocket.isClosed()) {
                        throw new AssertionError("Failed to serve metadata request", ignored);
                    }
                }
            }
        }

        private void handle(Socket socket) throws IOException {
            try (Socket acceptedSocket = socket) {
                acceptedSocket.setSoTimeout(5_000);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(acceptedSocket.getInputStream(), StandardCharsets.ISO_8859_1));
                String requestLine = reader.readLine();
                if (requestLine == null) {
                    return;
                }
                String key = metadataKey(requestLine);
                Map<String, String> headers = new HashMap<>();
                while (true) {
                    String headerLine = reader.readLine();
                    if (headerLine == null || headerLine.isEmpty()) {
                        break;
                    }
                    int separator = headerLine.indexOf(':');
                    if (separator > 0) {
                        String name = headerLine.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                        String value = headerLine.substring(separator + 1).trim();
                        headers.put(name, value);
                    }
                }
                requestHeaders
                        .computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet())
                        .add(Map.copyOf(headers));

                MetadataResponse response = responses.get(key);
                if (response == null) {
                    writeResponse(acceptedSocket.getOutputStream(), 404, "Not Found", "", true);
                    return;
                }
                writeResponse(
                        acceptedSocket.getOutputStream(), 200, "OK", response.body(), response.metadataFlavorHeader());
            }
        }

        private static String metadataKey(String requestLine) {
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                return "";
            }
            String target = parts[1];
            int metadataIndex = target.indexOf(METADATA_PREFIX);
            if (metadataIndex < 0) {
                return "";
            }
            String key = target.substring(metadataIndex + METADATA_PREFIX.length());
            int queryIndex = key.indexOf('?');
            if (queryIndex >= 0) {
                return key.substring(0, queryIndex);
            }
            return key;
        }

        private static void writeResponse(
                OutputStream outputStream, int status, String message, String body, boolean metadataFlavorHeader)
                throws IOException {
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            String metadataHeader = metadataFlavorHeader ? "Metadata-Flavor: Google\r\n" : "";
            String headers = "HTTP/1.1 " + status + " " + message + "\r\n"
                    + metadataHeader
                    + "Content-Type: text/plain; charset=utf-8\r\n"
                    + "Content-Length: " + bodyBytes.length + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";
            outputStream.write(headers.getBytes(StandardCharsets.ISO_8859_1));
            outputStream.write(bodyBytes);
            outputStream.flush();
        }

        private record MetadataResponse(String body, boolean metadataFlavorHeader) {}

        @Override
        public void close() throws Exception {
            serverSocket.close();
            executor.shutdownNow();
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                throw new AssertionError("Metadata proxy did not stop in time");
            }
        }
    }
}
