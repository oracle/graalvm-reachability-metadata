/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_management;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class Micronaut_managementTest {
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final Argument<Map<String, Object>> JSON_MAP = Argument.mapOf(String.class, Object.class);

    @Test
    @Timeout(55)
    void servesHealthAndHealthSelectorResponsesWithAnonymousDetails() {
        Map<String, Object> properties = Map.ofEntries(
                Map.entry("micronaut.server.port", -1),
                Map.entry("endpoints.all.enabled", false),
                Map.entry("endpoints.all.path", "/management/"),
                Map.entry("endpoints.health.enabled", true),
                Map.entry("endpoints.health.details-visible", "ANONYMOUS"),
                Map.entry("endpoints.health.disk-space.enabled", false),
                Map.entry("endpoints.health.deadlocked-threads.enabled", false),
                Map.entry("endpoints.health.discovery-client.enabled", false),
                Map.entry("endpoints.health.discovery-client-health.enabled", false));

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, properties, Environment.TEST);
                HttpClient client = HttpClient.create(server.getURL(), clientConfiguration())) {
            BlockingHttpClient blockingClient = client.toBlocking();

            assertThat(server.isRunning()).isTrue();
            assertThat(server.getPort()).isPositive();

            Map<String, Object> health = getJson(blockingClient, "/management/health");
            assertThat(health).containsEntry("status", "UP").containsKey("details");
            Map<String, Object> healthDetails = asMap(health.get("details"));
            assertThat(healthDetails).containsKeys("gracefulShutdown", "service");
            assertThat(asMap(healthDetails.get("service"))).containsEntry("status", "UP");

            Map<String, Object> liveness = getJson(blockingClient, "/management/health/liveness");
            assertThat(liveness)
                    .containsEntry("name", "application")
                    .containsEntry("status", "UNKNOWN")
                    .doesNotContainKey("details");

            Map<String, Object> readiness = getJson(blockingClient, "/management/health/readiness");
            assertThat(readiness).containsEntry("status", "UP").containsKey("details");
            assertThat(asMap(readiness.get("details"))).containsKeys("gracefulShutdown", "service");
        }
    }

    @Test
    @Timeout(55)
    void refreshesApplicationStateThroughWriteEndpoint() {
        Map<String, Object> properties = Map.ofEntries(
                Map.entry("micronaut.server.port", -1),
                Map.entry("endpoints.all.enabled", false),
                Map.entry("endpoints.all.path", "/management/"),
                Map.entry("endpoints.all.sensitive", false),
                Map.entry("endpoints.refresh.enabled", true));

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, properties, Environment.TEST);
                HttpClient client = HttpClient.create(server.getURL(), clientConfiguration())) {
            BlockingHttpClient blockingClient = client.toBlocking();
            HttpRequest<?> request = HttpRequest.POST("/management/refresh", Map.of("force", true))
                    .accept(MediaType.APPLICATION_JSON_TYPE);

            HttpResponse<List<String>> response = blockingClient.exchange(request, Argument.listOf(String.class));

            assertThat(response.code()).isEqualTo(HttpStatus.OK.getCode());
            assertThat(response.getContentType()).contains(MediaType.APPLICATION_JSON_TYPE);
            assertThat(response.body()).isEmpty();
        }
    }

    @Test
    @Timeout(55)
    void stopsRunningApplicationThroughWriteEndpoint() throws InterruptedException {
        Map<String, Object> properties = Map.ofEntries(
                Map.entry("micronaut.server.port", -1),
                Map.entry("endpoints.all.enabled", false),
                Map.entry("endpoints.all.path", "/management/"),
                Map.entry("endpoints.all.sensitive", false),
                Map.entry("endpoints.stop.enabled", true));

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, properties, Environment.TEST);
                HttpClient client = HttpClient.create(server.getURL(), clientConfiguration())) {
            HttpRequest<?> request = HttpRequest.create(HttpMethod.POST, "/management/stop")
                    .accept(MediaType.APPLICATION_JSON_TYPE);
            HttpResponse<Map<String, Object>> response = client.toBlocking().exchange(request, JSON_MAP);

            assertThat(response.code()).isEqualTo(HttpStatus.OK.getCode());
            assertThat(response.body()).containsEntry("message", "Server shutdown started");

            long stopDeadline = System.nanoTime() + HTTP_TIMEOUT.toNanos();
            while (server.isRunning() && System.nanoTime() < stopDeadline) {
                Thread.sleep(100);
            }
            assertThat(server.isRunning()).isFalse();
        }
    }

    @Test
    @Timeout(55)
    void servesConfiguredInformationAndInspectionEndpoints() {
        Map<String, Object> properties = Map.ofEntries(
                Map.entry("micronaut.server.port", -1),
                Map.entry("endpoints.all.enabled", false),
                Map.entry("endpoints.all.path", "/management/"),
                Map.entry("endpoints.all.sensitive", false),
                Map.entry("endpoints.beans.enabled", true),
                Map.entry("endpoints.env.enabled", true),
                Map.entry("endpoints.env.active-keys", List.of("activeEnvironments")),
                Map.entry("endpoints.info.enabled", true),
                Map.entry("endpoints.info.path", "/about"),
                Map.entry("endpoints.routes.enabled", true),
                Map.entry("info.application.name", "inventory-service"),
                Map.entry("info.application.owner", "platform-team"));

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, properties, Environment.TEST);
                HttpClient client = HttpClient.create(server.getURL(), clientConfiguration())) {
            BlockingHttpClient blockingClient = client.toBlocking();

            Map<String, Object> info = getJson(blockingClient, "/management/about");
            assertThat(asMap(info.get("application")))
                    .containsEntry("name", "inventory-service")
                    .containsEntry("owner", "platform-team");

            Map<String, Object> environment = getJson(blockingClient, "/management/env");
            assertThat(environment).hasSize(1).containsKey("activeEnvironments");
            assertThat(asList(environment.get("activeEnvironments"))).contains("test");

            Map<String, Object> beanReport = getJson(blockingClient, "/management/beans");
            assertThat(beanReport).containsKeys("beans", "disabled");
            Map<String, Object> beans = asMap(beanReport.get("beans"));
            assertThat(beans.values().stream()
                            .map(Micronaut_managementTest::asMap)
                            .map(bean -> bean.get("type"))
                            .anyMatch("io.micronaut.management.endpoint.info.InfoEndpoint"::equals))
                    .isTrue();

            Map<String, Object> routes = getJson(blockingClient, "/management/routes");
            assertThat(routes.keySet().stream()
                            .anyMatch(route -> route.contains("/management/about") && route.contains("method=[GET]")))
                    .isTrue();
            assertThat(routes.values().stream()
                            .anyMatch(route -> route.toString().contains("InfoEndpoint.getInfo")))
                    .isTrue();
            assertThat(routes.keySet().stream()
                            .anyMatch(route -> route.contains("/management/routes") && route.contains("method=[GET]")))
                    .isTrue();
        }
    }

    private static Map<String, Object> getJson(BlockingHttpClient client, String path) {
        HttpRequest<?> request = HttpRequest.GET(path).accept(MediaType.APPLICATION_JSON_TYPE);
        HttpResponse<Map<String, Object>> response = client.exchange(request, JSON_MAP);

        assertThat(response.code()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(response.getContentType()).contains(MediaType.APPLICATION_JSON_TYPE);
        assertThat(response.body()).isNotNull();
        return response.body();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        assertThat(value).isInstanceOf(List.class);
        return (List<Object>) value;
    }

    private static DefaultHttpClientConfiguration clientConfiguration() {
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
        configuration.setConnectTimeout(HTTP_TIMEOUT);
        configuration.setReadTimeout(HTTP_TIMEOUT);
        configuration.setRequestTimeout(HTTP_TIMEOUT);
        return configuration;
    }
}
