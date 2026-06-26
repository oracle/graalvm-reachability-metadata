/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_contrib.opentelemetry_gcp_resources;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.contrib.gcp.resource.GCPResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class Opentelemetry_gcp_resourcesTest {
    private static final AttributeKey<String> CLOUD_ACCOUNT_ID =
            AttributeKey.stringKey("cloud.account.id");
    private static final AttributeKey<String> CLOUD_AVAILABILITY_ZONE =
            AttributeKey.stringKey("cloud.availability_zone");
    private static final AttributeKey<String> CLOUD_PLATFORM = AttributeKey.stringKey("cloud.platform");
    private static final AttributeKey<String> CLOUD_PROVIDER = AttributeKey.stringKey("cloud.provider");
    private static final AttributeKey<String> CLOUD_REGION = AttributeKey.stringKey("cloud.region");
    private static final AttributeKey<String> FAAS_INSTANCE = AttributeKey.stringKey("faas.instance");
    private static final AttributeKey<String> FAAS_NAME = AttributeKey.stringKey("faas.name");
    private static final AttributeKey<String> FAAS_VERSION = AttributeKey.stringKey("faas.version");
    private static final AttributeKey<String> GCP_CLOUD_RUN_JOB_EXECUTION =
            AttributeKey.stringKey("gcp.cloud_run.job.execution");
    private static final AttributeKey<Long> GCP_CLOUD_RUN_JOB_TASK_INDEX =
            AttributeKey.longKey("gcp.cloud_run.job.task_index");
    private static final AttributeKey<String> GCP_GCE_INSTANCE_HOSTNAME =
            AttributeKey.stringKey("gcp.gce.instance.hostname");
    private static final AttributeKey<String> GCP_GCE_INSTANCE_NAME =
            AttributeKey.stringKey("gcp.gce.instance.name");
    private static final AttributeKey<String> HOST_ID = AttributeKey.stringKey("host.id");
    private static final AttributeKey<String> HOST_NAME = AttributeKey.stringKey("host.name");
    private static final AttributeKey<String> HOST_TYPE = AttributeKey.stringKey("host.type");
    private static final AttributeKey<String> K8S_CLUSTER_NAME = AttributeKey.stringKey("k8s.cluster.name");

    private static final String PROJECT_ID = "native-image-project";
    private static final String ZONE = "us-central1-a";
    private static final String REGION = "us-central1";
    private static final String INSTANCE_ID = "987654321";
    private static final String INSTANCE_NAME = "test-gce-instance";
    private static final String INSTANCE_HOSTNAME = "test-gce-instance.c.native-image-project.internal";
    private static final String MACHINE_TYPE = "e2-medium";
    private static final String CLUSTER_NAME = "native-image-cluster";

    @Test
    void serviceLoaderDiscoversGcpResourceProvider() {
        ServiceLoader<ResourceProvider> resourceProviders = ServiceLoader.load(ResourceProvider.class);

        assertThat(resourceProviders)
                .anySatisfy(provider -> assertThat(provider).isInstanceOf(GCPResourceProvider.class));
    }

    @Test
    void shouldApplyOnlyWhenExistingResourceHasNoCloudProvider() {
        GCPResourceProvider provider = new GCPResourceProvider();
        Resource resourceWithoutCloudProvider = Resource.empty();
        Resource resourceWithCloudProvider = Resource.builder().put(CLOUD_PROVIDER, "aws").build();

        assertThat(provider.shouldApply(null, resourceWithoutCloudProvider)).isTrue();
        assertThat(provider.shouldApply(null, resourceWithCloudProvider)).isFalse();
    }

    @Test
    void createsResourceFromMetadataServerAttributes() throws Exception {
        try (MetadataProxyServer metadataServer = MetadataProxyServer.start();
                ProxyProperties ignored = ProxyProperties.use(metadataServer.getPort())) {
            GCPResourceProvider provider = new GCPResourceProvider();

            Attributes attributes = provider.getAttributes();
            Resource resource = provider.createResource(null);

            assertCommonGcpAttributes(attributes);
            assertPlatformSpecificAttributes(attributes);
            assertThat(resource.getAttributes().asMap()).containsAllEntriesOf(attributes.asMap());
        }
    }

    private static void assertCommonGcpAttributes(Attributes attributes) {
        assertThat(attributes.get(CLOUD_PROVIDER)).isEqualTo("gcp");
        assertThat(attributes.get(CLOUD_ACCOUNT_ID)).isEqualTo(PROJECT_ID);
        assertThat(attributes.get(CLOUD_PLATFORM)).isEqualTo(expectedCloudPlatform());
    }

    private static void assertPlatformSpecificAttributes(Attributes attributes) {
        switch (expectedCloudPlatform()) {
            case "gcp_kubernetes_engine":
                assertThat(attributes.get(K8S_CLUSTER_NAME)).isEqualTo(CLUSTER_NAME);
                assertThat(attributes.get(HOST_ID)).isEqualTo(INSTANCE_ID);
                assertThat(attributes.get(CLOUD_AVAILABILITY_ZONE)).isEqualTo(ZONE);
                break;
            case "gcp_cloud_run":
                assertThat(attributes.get(FAAS_INSTANCE)).isEqualTo(INSTANCE_ID);
                assertThat(attributes.get(CLOUD_REGION)).isEqualTo(REGION);
                assertCloudRunAttributes(attributes);
                break;
            case "gcp_cloud_functions":
                assertThat(attributes.get(FAAS_INSTANCE)).isEqualTo(INSTANCE_ID);
                assertThat(attributes.get(CLOUD_AVAILABILITY_ZONE)).isEqualTo(ZONE);
                assertThat(attributes.get(CLOUD_REGION)).isEqualTo(REGION);
                assertOptionalStringAttribute(attributes, FAAS_NAME, System.getenv("K_SERVICE"));
                assertOptionalStringAttribute(attributes, FAAS_VERSION, System.getenv("K_REVISION"));
                break;
            case "gcp_app_engine":
                assertThat(attributes.get(CLOUD_AVAILABILITY_ZONE)).isEqualTo(ZONE);
                assertThat(attributes.get(CLOUD_REGION)).isEqualTo(REGION);
                assertOptionalStringAttribute(attributes, FAAS_NAME, System.getenv("GAE_SERVICE"));
                assertOptionalStringAttribute(attributes, FAAS_VERSION, System.getenv("GAE_VERSION"));
                assertOptionalStringAttribute(attributes, FAAS_INSTANCE, System.getenv("GAE_INSTANCE"));
                break;
            default:
                assertThat(attributes.get(CLOUD_AVAILABILITY_ZONE)).isEqualTo(ZONE);
                assertThat(attributes.get(CLOUD_REGION)).isEqualTo(REGION);
                assertThat(attributes.get(HOST_ID)).isEqualTo(INSTANCE_ID);
                assertThat(attributes.get(HOST_NAME)).isEqualTo(INSTANCE_NAME);
                assertThat(attributes.get(GCP_GCE_INSTANCE_NAME)).isEqualTo(INSTANCE_NAME);
                assertThat(attributes.get(GCP_GCE_INSTANCE_HOSTNAME)).isEqualTo(INSTANCE_HOSTNAME);
                assertThat(attributes.get(HOST_TYPE)).isEqualTo(MACHINE_TYPE);
                break;
        }
    }

    private static void assertCloudRunAttributes(Attributes attributes) {
        if (isDetectedCloudRunJob()) {
            assertThat(attributes.get(FAAS_NAME)).isEqualTo(System.getenv("CLOUD_RUN_JOB"));
            assertOptionalStringAttribute(
                    attributes, GCP_CLOUD_RUN_JOB_EXECUTION, System.getenv("CLOUD_RUN_EXECUTION"));
            String taskIndex = System.getenv("CLOUD_RUN_TASK_INDEX");
            if (taskIndex != null) {
                assertThat(attributes.get(GCP_CLOUD_RUN_JOB_TASK_INDEX)).isEqualTo(Long.parseLong(taskIndex));
            }
        } else {
            assertThat(attributes.get(CLOUD_AVAILABILITY_ZONE)).isEqualTo(ZONE);
            assertOptionalStringAttribute(attributes, FAAS_NAME, System.getenv("K_SERVICE"));
            assertOptionalStringAttribute(attributes, FAAS_VERSION, System.getenv("K_REVISION"));
        }
    }

    private static void assertOptionalStringAttribute(
            Attributes attributes, AttributeKey<String> attributeKey, String expectedValue) {
        if (expectedValue == null) {
            assertThat(attributes.get(attributeKey)).isNull();
        } else {
            assertThat(attributes.get(attributeKey)).isEqualTo(expectedValue);
        }
    }

    private static String expectedCloudPlatform() {
        Map<String, String> env = System.getenv();
        if (env.get("KUBERNETES_SERVICE_HOST") != null) {
            return "gcp_kubernetes_engine";
        }
        if (env.get("K_CONFIGURATION") != null && env.get("FUNCTION_TARGET") == null) {
            return "gcp_cloud_run";
        }
        if (env.get("FUNCTION_TARGET") != null) {
            return "gcp_cloud_functions";
        }
        if (env.get("CLOUD_RUN_JOB") != null) {
            return "gcp_cloud_run";
        }
        if (env.get("GAE_SERVICE") != null) {
            return "gcp_app_engine";
        }
        return "gcp_compute_engine";
    }

    private static boolean isDetectedCloudRunJob() {
        Map<String, String> env = System.getenv();
        return env.get("KUBERNETES_SERVICE_HOST") == null
                && env.get("K_CONFIGURATION") == null
                && env.get("FUNCTION_TARGET") == null
                && env.get("CLOUD_RUN_JOB") != null;
    }

    private static final class ProxyProperties implements AutoCloseable {
        private static final String PROXY_HOST = "http.proxyHost";
        private static final String PROXY_PORT = "http.proxyPort";
        private static final String NON_PROXY_HOSTS = "http.nonProxyHosts";

        private final Map<String, String> originalProperties;

        private ProxyProperties(Map<String, String> originalProperties) {
            this.originalProperties = originalProperties;
        }

        static ProxyProperties use(int port) {
            Map<String, String> originalProperties = new HashMap<>();
            remember(originalProperties, PROXY_HOST);
            remember(originalProperties, PROXY_PORT);
            remember(originalProperties, NON_PROXY_HOSTS);
            System.setProperty(PROXY_HOST, "127.0.0.1");
            System.setProperty(PROXY_PORT, Integer.toString(port));
            System.setProperty(NON_PROXY_HOSTS, "");
            return new ProxyProperties(originalProperties);
        }

        private static void remember(Map<String, String> originalProperties, String key) {
            originalProperties.put(key, System.getProperty(key));
        }

        @Override
        public void close() {
            originalProperties.forEach(
                    (key, value) -> {
                        if (value == null) {
                            System.clearProperty(key);
                        } else {
                            System.setProperty(key, value);
                        }
                    });
        }
    }

    private static final class MetadataProxyServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executorService;

        private MetadataProxyServer(ServerSocket serverSocket, ExecutorService executorService) {
            this.serverSocket = serverSocket;
            this.executorService = executorService;
        }

        static MetadataProxyServer start() throws IOException {
            ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
            serverSocket.setSoTimeout(500);
            ExecutorService executorService = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "gcp-metadata-proxy");
                thread.setDaemon(true);
                return thread;
            });
            MetadataProxyServer server = new MetadataProxyServer(serverSocket, executorService);
            executorService.submit(server::acceptConnections);
            return server;
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        private void acceptConnections() {
            while (!serverSocket.isClosed()) {
                try (Socket socket = serverSocket.accept()) {
                    handle(socket);
                } catch (IOException ignored) {
                    // Closing the server socket is the normal shutdown path.
                }
            }
        }

        private static void handle(Socket socket) throws IOException {
            socket.setSoTimeout(2_000);
            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String requestLine = reader.readLine();
            if (requestLine == null) {
                return;
            }
            String headerLine;
            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                // Drain request headers so the client can receive the response.
            }
            String value = metadataValue(requestLine);
            byte[] body = value.getBytes(StandardCharsets.UTF_8);
            String response = "HTTP/1.1 200 OK\r\n"
                    + "Metadata-Flavor: Google\r\n"
                    + "Content-Type: text/plain; charset=utf-8\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(response.getBytes(StandardCharsets.UTF_8));
            outputStream.write(body);
            outputStream.flush();
        }

        private static String metadataValue(String requestLine) {
            if (requestLine.contains("/project/project-id")) {
                return PROJECT_ID;
            }
            if (requestLine.contains("/instance/zone")) {
                return "projects/123456789/zones/" + ZONE;
            }
            if (requestLine.contains("/instance/region")) {
                return "projects/123456789/regions/" + REGION;
            }
            if (requestLine.contains("/instance/id")) {
                return INSTANCE_ID;
            }
            if (requestLine.contains("/instance/name")) {
                return INSTANCE_NAME;
            }
            if (requestLine.contains("/instance/hostname")) {
                return INSTANCE_HOSTNAME;
            }
            if (requestLine.contains("/instance/machine-type")) {
                return "projects/123456789/machineTypes/" + MACHINE_TYPE;
            }
            if (requestLine.contains("/instance/attributes/cluster-name")) {
                return CLUSTER_NAME;
            }
            if (requestLine.contains("/instance/attributes/cluster-location")) {
                return ZONE;
            }
            return "";
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            executorService.shutdownNow();
            assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
