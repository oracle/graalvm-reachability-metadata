/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.util.Headers;
import io.undertow.util.PathTemplateMatch;
import io.undertow.util.StatusCodes;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * HTTP request routing with `RoutingHandler`: path templates, dispatch by method, path
 * parameters read from the `PathTemplateMatch` attachment, and the fallback and invalid-method
 * handlers.
 *
 * A small task board exposes a JSON API on an embedded Undertow server bound to an ephemeral
 * port. Clients create tasks, list them, fetch and complete one by id, and delete them; an
 * unknown path answers 404 and a known path called with the wrong method answers 405 with an
 * `Allow` header. The client is the JDK `HttpClient`, so every assertion is on what goes over the
 * wire.
 */
public class TaskBoardRoutingHandlerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Undertow server;
    private HttpClient client;
    private URI base;

    @BeforeEach
    void startServer() {
        TaskBoard board = new TaskBoard();
        RoutingHandler routes = Handlers.routing()
                .get("/tasks", board::list)
                .post("/tasks", board::create)
                .get("/tasks/{id}", board::show)
                .put("/tasks/{id}/done", board::complete)
                .delete("/tasks/{id}", board::remove)
                .setFallbackHandler(exchange -> error(exchange, StatusCodes.NOT_FOUND, "no route"))
                .setInvalidMethodHandler(exchange -> {
                    exchange.getResponseHeaders().put(Headers.ALLOW, allowedMethods(exchange));
                    error(exchange, StatusCodes.METHOD_NOT_ALLOWED, "method not allowed");
                });
        server = Undertow.builder()
                .addHttpListener(0, "127.0.0.1")
                .setHandler(routes)
                .build();
        server.start();
        InetSocketAddress address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
        base = URI.create("http://127.0.0.1:" + address.getPort());
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void createsListsAndFetchesTasksByPathTemplate() throws Exception {
        HttpResponse<String> created = send("POST", "/tasks", """
                {"title": "Write release notes"}
                """);
        assertEquals(201, created.statusCode());
        assertEquals("/tasks/1", created.headers().firstValue("Location").orElseThrow());
        JsonNode first = JSON.readTree(created.body());
        assertEquals(1, first.get("id").asLong());
        assertEquals("Write release notes", first.get("title").asText());
        assertEquals(false, first.get("done").asBoolean());

        assertEquals(201, send("POST", "/tasks", """
                {"title": "Tag the build"}
                """).statusCode());

        HttpResponse<String> listed = send("GET", "/tasks", null);
        assertEquals(200, listed.statusCode());
        assertEquals("application/json", listed.headers().firstValue("Content-Type").orElseThrow());
        JsonNode tasks = JSON.readTree(listed.body());
        assertEquals(2, tasks.size());
        assertEquals("Write release notes", tasks.get(0).get("title").asText());
        assertEquals("Tag the build", tasks.get(1).get("title").asText());

        HttpResponse<String> fetched = send("GET", "/tasks/2", null);
        assertEquals(200, fetched.statusCode());
        assertEquals("Tag the build", JSON.readTree(fetched.body()).get("title").asText());
    }

    @Test
    void completesAndDeletesATaskThroughNestedTemplates() throws Exception {
        send("POST", "/tasks", """
                {"title": "Rotate credentials"}
                """);

        HttpResponse<String> completed = send("PUT", "/tasks/1/done", null);
        assertEquals(200, completed.statusCode());
        assertTrue(JSON.readTree(completed.body()).get("done").asBoolean());
        assertTrue(JSON.readTree(send("GET", "/tasks/1", null).body()).get("done").asBoolean());

        HttpResponse<String> deleted = send("DELETE", "/tasks/1", null);
        assertEquals(204, deleted.statusCode());
        assertEquals("", deleted.body());

        HttpResponse<String> gone = send("GET", "/tasks/1", null);
        assertEquals(404, gone.statusCode());
        assertEquals("task 1 not found", JSON.readTree(gone.body()).get("error").asText());
        assertEquals(0, JSON.readTree(send("GET", "/tasks", null).body()).size());
    }

    @Test
    void rejectsMalformedRequestsWithClientErrors() throws Exception {
        HttpResponse<String> untitled = send("POST", "/tasks", """
                {"owner": "ops"}
                """);
        assertEquals(400, untitled.statusCode());
        assertEquals("title is required", JSON.readTree(untitled.body()).get("error").asText());

        HttpResponse<String> badId = send("GET", "/tasks/latest", null);
        assertEquals(400, badId.statusCode());
        assertEquals("task id must be numeric", JSON.readTree(badId.body()).get("error").asText());
    }

    @Test
    void answersUnknownPathsAndWrongMethodsFromTheConfiguredHandlers() throws Exception {
        HttpResponse<String> unknown = send("GET", "/projects", null);
        assertEquals(404, unknown.statusCode());
        assertEquals("no route", JSON.readTree(unknown.body()).get("error").asText());

        HttpResponse<String> wrongMethod = send("PATCH", "/tasks/1", "{}");
        assertEquals(405, wrongMethod.statusCode());
        assertEquals("DELETE, GET", wrongMethod.headers().firstValue("Allow").orElseThrow());
        assertEquals("method not allowed", JSON.readTree(wrongMethod.body()).get("error").asText());
    }

    private HttpResponse<String> send(String method, String path, String body)
            throws IOException, InterruptedException {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest request = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .method(method, publisher)
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /// The methods the board routes for the requested path, as a client would need them in `Allow`.
    private static String allowedMethods(HttpServerExchange exchange) {
        String path = exchange.getRelativePath();
        if (path.equals("/tasks")) {
            return "GET, POST";
        }
        return path.endsWith("/done") ? "PUT" : "DELETE, GET";
    }

    private static void json(HttpServerExchange exchange, int status, JsonNode body) {
        exchange.setStatusCode(status);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
        exchange.getResponseSender().send(body.toString());
    }

    private static void error(HttpServerExchange exchange, int status, String message) {
        json(exchange, status, JSON.createObjectNode().put("error", message));
    }

    /// The application's in-memory task store and the handlers the routes dispatch to.
    private static final class TaskBoard {

        private final Map<Long, ObjectNode> tasks = new ConcurrentHashMap<>();
        private final AtomicLong ids = new AtomicLong();

        void list(HttpServerExchange exchange) {
            ArrayNode body = JSON.createArrayNode();
            new TreeMap<>(tasks).values().forEach(body::add);
            json(exchange, StatusCodes.OK, body);
        }

        void create(HttpServerExchange exchange) {
            exchange.getRequestReceiver().receiveFullString((ex, payload) -> {
                JsonNode request;
                try {
                    request = JSON.readTree(payload);
                } catch (IOException e) {
                    error(ex, StatusCodes.BAD_REQUEST, "body is not JSON");
                    return;
                }
                if (!request.hasNonNull("title")) {
                    error(ex, StatusCodes.BAD_REQUEST, "title is required");
                    return;
                }
                long id = ids.incrementAndGet();
                ObjectNode task = JSON.createObjectNode()
                        .put("id", id)
                        .put("title", request.get("title").asText())
                        .put("done", false);
                tasks.put(id, task);
                ex.getResponseHeaders().put(Headers.LOCATION, "/tasks/" + id);
                json(ex, StatusCodes.CREATED, task);
            });
        }

        void show(HttpServerExchange exchange) {
            ObjectNode task = find(exchange);
            if (task != null) {
                json(exchange, StatusCodes.OK, task);
            }
        }

        void complete(HttpServerExchange exchange) {
            ObjectNode task = find(exchange);
            if (task != null) {
                ObjectNode done = task.deepCopy().put("done", true);
                tasks.put(done.get("id").asLong(), done);
                json(exchange, StatusCodes.OK, done);
            }
        }

        void remove(HttpServerExchange exchange) {
            ObjectNode task = find(exchange);
            if (task != null) {
                tasks.remove(task.get("id").asLong());
                exchange.setStatusCode(StatusCodes.NO_CONTENT);
                exchange.endExchange();
            }
        }

        /// Resolves `{id}` from the matched template, answering the error itself when it fails.
        private ObjectNode find(HttpServerExchange exchange) {
            PathTemplateMatch match = exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY);
            String raw = match.getParameters().get("id");
            long id;
            try {
                id = Long.parseLong(raw);
            } catch (NumberFormatException e) {
                error(exchange, StatusCodes.BAD_REQUEST, "task id must be numeric");
                return null;
            }
            ObjectNode task = tasks.get(id);
            if (task == null) {
                error(exchange, StatusCodes.NOT_FOUND, "task " + id + " not found");
            }
            return task;
        }
    }
}
