/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_microcks.microcks_testcontainers;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.command.InspectContainerResponse;
import io.github.microcks.testcontainers.MicrocksContainer;
import io.github.microcks.testcontainers.model.ServiceRef;
import io.github.microcks.testcontainers.model.TestRequest;
import io.github.microcks.testcontainers.model.TestResult;
import io.github.microcks.testcontainers.model.TestRunnerType;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class MicrocksContainerTest {
    private static final String IMAGE = "quay.io/microcks/microcks-uber:1.14.0";
    private static final String OPEN_API =
            "io_github_microcks/microcks_testcontainers/greeting-openapi.yaml";
    private static final String SNAPSHOT =
            "io_github_microcks/microcks_testcontainers/repository-snapshot.json";
    private static final String SERVICE_NAME = "GreetingAPI";
    private static final String SERVICE_VERSION = "1.0.0";
    private static final int IO_TIMEOUT_MILLIS = 10_000;

    @Test
    void importsClasspathResourcesAndExercisesMockAndTestApis() throws Exception {
        try (FallbackResourceMicrocksContainer microcks =
                new FallbackResourceMicrocksContainer()) {
            microcks.start();

            List<ServiceRef> services = microcks.getServices();
            assertThat(services)
                    .anySatisfy(service -> {
                        assertThat(service.getName()).isEqualTo("SnapshotCatalog");
                        assertThat(service.getVersion()).isEqualTo(SERVICE_VERSION);
                    })
                    .anySatisfy(service -> {
                        assertThat(service.getName()).isEqualTo(SERVICE_NAME);
                        assertThat(service.getVersion()).isEqualTo(SERVICE_VERSION);
                    });

            String mockEndpoint = microcks.getRestMockEndpoint(SERVICE_NAME, SERVICE_VERSION);
            assertThat(mockEndpoint)
                    .isEqualTo(microcks.getHttpEndpoint() + "/rest/GreetingAPI/1.0.0");
            assertThat(get(mockEndpoint + "/hello"))
                    .contains("\"message\"", "Hello from Microcks");

            awaitInvocation(microcks);
            assertThat(microcks.verify(SERVICE_NAME, SERVICE_VERSION)).isTrue();
            assertThat(microcks.getServiceInvocationsCount(SERVICE_NAME, SERVICE_VERSION))
                    .isGreaterThanOrEqualTo(1L);

            String internalMockEndpoint =
                    "http://localhost:8080/rest/GreetingAPI/1.0.0";
            TestRequest request = new TestRequest.Builder()
                    .serviceId(SERVICE_NAME + ":" + SERVICE_VERSION)
                    .testEndpoint(internalMockEndpoint)
                    .runnerType(TestRunnerType.OPEN_API_SCHEMA)
                    .timeout(Duration.ofSeconds(10))
                    .build();

            assertThat(request.getRunnerType()).isEqualTo(TestRunnerType.OPEN_API_SCHEMA.name());
            assertThat(request.getTimeout()).isEqualTo(10_000L);

            TestResult result = microcks.testEndpoint(request);
            assertThat(result.isInProgress()).isFalse();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getTestedEndpoint()).isEqualTo(internalMockEndpoint);
            assertThat(result.getTestCaseResults()).isNotEmpty();
        }
    }

    private static String get(String endpoint) throws IOException {
        HttpURLConnection connection =
                (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
        connection.setConnectTimeout(IO_TIMEOUT_MILLIS);
        connection.setReadTimeout(IO_TIMEOUT_MILLIS);
        connection.setRequestMethod("GET");
        try {
            assertThat(connection.getResponseCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            try (InputStream input = connection.getInputStream()) {
                return new String(input.readAllBytes(), UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void awaitInvocation(MicrocksContainer microcks) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!microcks.verify(SERVICE_NAME, SERVICE_VERSION)
                && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(200);
        }
    }

    private static final class FallbackResourceMicrocksContainer extends MicrocksContainer {
        private FallbackResourceMicrocksContainer() {
            super(IMAGE);
            withSnapshots(SNAPSHOT);
            withMainArtifacts(OPEN_API);
        }

        @Override
        protected void containerIsStarted(InspectContainerResponse containerInfo) {
            Thread thread = Thread.currentThread();
            ClassLoader originalClassLoader = thread.getContextClassLoader();
            thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
            try {
                super.containerIsStarted(containerInfo);
            } finally {
                thread.setContextClassLoader(originalClassLoader);
            }
        }
    }
}
