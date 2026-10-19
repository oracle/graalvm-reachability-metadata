/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_consul_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.cloud.consul.ConsulAutoConfiguration;
import org.springframework.cloud.consul.ConsulClient;
import org.springframework.cloud.consul.model.http.agent.Service;
import org.springframework.cloud.consul.model.http.catalog.CatalogService;
import org.springframework.cloud.consul.model.http.catalog.Node;
import org.springframework.cloud.consul.model.http.health.Check;
import org.springframework.cloud.consul.model.http.health.HealthService;
import org.springframework.cloud.consul.model.http.kv.GetValue;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;

@Timeout(value = 55, unit = TimeUnit.SECONDS)
public class ConsulClientTest {

    private HttpServer server;

    private ConsulClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new ConsulHandler());
        server.start();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        RestClient restClient = RestClient.builder()
            .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
            .requestFactory(requestFactory)
            .build();
        client = ConsulAutoConfiguration.createNewConsulClient(RestClientAdapter.create(restClient));
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void readsAndDecodesKeyValueEntries() {
        ResponseEntity<List<GetValue>> response = client.getKVValue("config/app/data", "test-token");

        assertThat(response.getHeaders().getFirst("X-Consul-Index")).isEqualTo("42");
        assertThat(response.getBody()).hasSize(1);
        GetValue value = response.getBody().get(0);
        assertThat(value.getKey()).isEqualTo("config/app/data");
        assertThat(value.getDecodedValue()).isEqualTo("feature.enabled=true");
    }

    @Test
    void readsAgentServices() {
        ResponseEntity<Map<String, Service>> response = client.getAgentServices();

        assertThat(response.getBody()).containsOnlyKeys("inventory-1");
        Service service = response.getBody().get("inventory-1");
        assertThat(service.getId()).isEqualTo("inventory-1");
        assertThat(service.getService()).isEqualTo("inventory");
        assertThat(service.getTags()).containsExactly("primary", "http");
        assertThat(service.getAddress()).isEqualTo("127.0.0.1");
        assertThat(service.getPort()).isEqualTo(8080);
    }

    @Test
    void readsCatalogNodesAndServices() {
        List<Node> nodes = client.getCatalogNodes().getBody();
        List<CatalogService> services = client.getCatalogService("inventory").getBody();

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).getNode()).isEqualTo("node-a");
        assertThat(nodes.get(0).getDatacenter()).isEqualTo("dc1");
        assertThat(services).hasSize(1);
        assertThat(services.get(0).getServiceName()).isEqualTo("inventory");
        assertThat(services.get(0).getServiceTags()).containsExactly("primary");
        assertThat(services.get(0).getServicePort()).isEqualTo(8080);
    }

    @Test
    void readsHealthChecksAndServiceDetails() {
        List<Check> checks = client.getHealthChecksForService("inventory").getBody();
        List<HealthService> services = client.getHealthServices("inventory").getBody();

        assertThat(checks).hasSize(1);
        assertThat(checks.get(0).getCheckId()).isEqualTo("service:inventory-1");
        assertThat(checks.get(0).getStatus()).isEqualTo(Check.CheckStatus.PASSING);
        assertThat(services).hasSize(1);
        HealthService healthService = services.get(0);
        assertThat(healthService.getNode().getNode()).isEqualTo("node-a");
        assertThat(healthService.getService().getService()).isEqualTo("inventory");
        assertThat(healthService.getChecks().get(0).getStatus()).isEqualTo(Check.CheckStatus.PASSING);
    }

    private static final class ConsulHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = responseFor(exchange.getRequestURI().getPath());
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("X-Consul-Index", "42");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        }

        private static String responseFor(String path) {
            if (path.startsWith("/v1/kv/")) {
                return """
                    [{"Key":"config/app/data","Value":"ZmVhdHVyZS5lbmFibGVkPXRydWU=",\
                    "CreateIndex":1,"ModifyIndex":2}]
                    """;
            }
            return switch (path) {
                case "/v1/agent/services" -> """
                    {"inventory-1":{"ID":"inventory-1","Service":"inventory",\
                    "Tags":["primary","http"],"Address":"127.0.0.1","Meta":{"zone":"test"},\
                    "Port":8080,"EnableTagOverride":false,"CreateIndex":1,"ModifyIndex":2}}
                    """;
                case "/v1/catalog/nodes" -> """
                    [{"ID":"node-id","Node":"node-a","Address":"127.0.0.1","Datacenter":"dc1",\
                    "TaggedAddresses":{"lan":"127.0.0.1"},"Meta":{"zone":"test"},\
                    "CreateIndex":1,"ModifyIndex":2}]
                    """;
                case "/v1/catalog/service/inventory" -> """
                    [{"ID":"node-id","Node":"node-a","Address":"127.0.0.1","Datacenter":"dc1",\
                    "ServiceID":"inventory-1","ServiceName":"inventory","ServiceTags":["primary"],\
                    "ServiceAddress":"127.0.0.1","ServicePort":8080,"CreateIndex":1,"ModifyIndex":2}]
                    """;
                case "/v1/health/checks/inventory" -> checkResponse();
                case "/v1/health/service/inventory" -> """
                    [{"Node":{"ID":"node-id","Node":"node-a","Address":"127.0.0.1",\
                    "Datacenter":"dc1","CreateIndex":1,"ModifyIndex":2},\
                    "Service":{"ID":"inventory-1","Service":"inventory","Tags":["primary"],\
                    "Address":"127.0.0.1","Port":8080,"CreateIndex":1,"ModifyIndex":2},\
                    "Checks":%s}]
                    """.formatted(checkResponse());
                default -> throw new IllegalArgumentException("Unexpected Consul request path: " + path);
            };
        }

        private static String checkResponse() {
            return """
                [{"Node":"node-a","CheckID":"service:inventory-1","Name":"Service health",\
                "Status":"passing","Notes":"","Output":"ok","ServiceID":"inventory-1",\
                "ServiceName":"inventory","ServiceTags":["primary"],"CreateIndex":1,"ModifyIndex":2}]
                """;
        }
    }
}
