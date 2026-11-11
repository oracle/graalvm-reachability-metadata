/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_agentscope.agentscope_extensions_studio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.agentscope.core.studio.StudioClient;
import io.agentscope.core.studio.StudioConfig;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

final class StudioServer implements AutoCloseable {
    private final HttpServer server;
    private final BlockingQueue<ReceivedRequest> requests = new LinkedBlockingQueue<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final StudioClient client;

    StudioServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/trpc/", this::handle);
        server.start();
        StudioConfig config = StudioConfig.builder()
                .studioUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .runId("studio-test-run")
                .project("Support \"project\"")
                .runName("Public API run")
                .maxRetries(0)
                .build();
        client = new StudioClient(config);
    }

    StudioClient client() {
        return client;
    }

    JsonNode takePayload(String path) throws Exception {
        ReceivedRequest request = requests.poll(10, TimeUnit.SECONDS);
        assertNotNull(request, "Studio should receive the request");
        assertEquals("POST", request.method());
        assertEquals(path, request.path());
        assertNotNull(request.contentType());
        assertTrue(request.contentType().startsWith("application/json"));
        return mapper.readTree(request.body());
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new ReceivedRequest(exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Content-Type"), body));
            exchange.sendResponseHeaders(204, -1);
        }
    }

    @Override
    public void close() {
        try {
            client.shutdown();
        } finally {
            server.stop(0);
        }
    }

    private record ReceivedRequest(String method, String path, String contentType, String body) {
    }
}
