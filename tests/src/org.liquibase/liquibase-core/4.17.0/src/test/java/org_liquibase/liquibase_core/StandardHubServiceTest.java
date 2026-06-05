/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import liquibase.Scope;
import liquibase.hub.HubConfiguration;
import liquibase.hub.core.StandardHubService;
import liquibase.hub.model.Connection;
import liquibase.hub.model.Project;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardHubServiceTest {

    @Test
    void getConnectionsBuildsSearchQueryFromConnectionFields() throws Exception {
        UUID organizationId = UUID.fromString("c71c40bc-87d8-45bb-809d-11344f88b740");
        AtomicReference<String> capturedSearch = new AtomicReference<>();

        try (HubTestServer server = HubTestServer.start(organizationId, capturedSearch)) {
            Connection searchConnection = new Connection()
                    .setJdbcUrl("jdbc:h2:mem:hub-search")
                    .setName("native image connection")
                    .setDescription("reports \"hub\" search")
                    .setProject(new Project().setName("native image project"));

            List<Connection> connections = Scope.child(Map.of(
                    HubConfiguration.LIQUIBASE_HUB_URL.getKey(), server.baseUrl(),
                    HubConfiguration.LIQUIBASE_HUB_API_KEY.getKey(), "test-api-key"),
                    () -> new StandardHubService().getConnections(searchConnection));

            assertThat(connections).isEmpty();
            assertThat(capturedSearch.get())
                    .contains("jdbcUrl:\"jdbc:h2:mem:hub-search\"")
                    .contains("name:\"native image connection\"")
                    .contains("description:\"reports \\\"hub\\\" search\"")
                    .contains("project.name:\"native image project\"");
        }
    }

    private static final class HubTestServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final String baseUrl;

        private HubTestServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
            this.baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private static HubTestServer start(UUID organizationId, AtomicReference<String> capturedSearch)
                throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            HubTestServer hubServer = new HubTestServer(server, executor);
            server.createContext("/", new HubHandler(organizationId, capturedSearch));
            server.setExecutor(executor);
            server.start();
            return hubServer;
        }

        private String baseUrl() {
            return baseUrl;
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private static final class HubHandler implements HttpHandler {
        private final UUID organizationId;
        private final AtomicReference<String> capturedSearch;

        private HubHandler(UUID organizationId, AtomicReference<String> capturedSearch) {
            this.organizationId = organizationId;
            this.capturedSearch = capturedSearch;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/api/v1/organizations")) {
                    sendJson(exchange, "{\"content\":[{\"id\":\"" + organizationId + "\",\"name\":\"test org\"}]}");
                    return;
                }
                if (path.equals("/api/v1/organizations/" + organizationId + "/connections")) {
                    capturedSearch.set(searchParameter(exchange.getRequestURI().getRawQuery()));
                    sendJson(exchange, "{\"content\":[]}");
                    return;
                }
                sendJson(exchange, 404, "{\"message\":\"not found\"}");
            } finally {
                exchange.close();
            }
        }

        private static String searchParameter(String rawQuery) {
            if (rawQuery == null) {
                return null;
            }
            for (String parameter : rawQuery.split("&")) {
                String[] parts = parameter.split("=", 2);
                if (parts.length == 2 && parts[0].equals("search")) {
                    return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                }
            }
            return null;
        }

        private static void sendJson(HttpExchange exchange, String body) throws IOException {
            sendJson(exchange, 200, body);
        }

        private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response);
            }
        }
    }
}
