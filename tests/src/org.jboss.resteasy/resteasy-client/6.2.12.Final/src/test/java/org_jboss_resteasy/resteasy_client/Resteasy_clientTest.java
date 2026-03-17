/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_client;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static jakarta.ws.rs.client.ClientBuilder.newClient;
import static org.assertj.core.api.Assertions.assertThat;

class Resteasy_clientTest {

    private HttpServer server;
    private Client client;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldExecuteGetWithQueryParametersAndReadResponseMetadata() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/hello", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestURI().getRawQuery()).isEqualTo("name=restEasy&lang=java");

            Headers responseHeaders = exchange.getResponseHeaders();
            responseHeaders.add("X-Test-Header", "ok");
            responseHeaders.add("Content-Type", "text/plain; charset=UTF-8");

            byte[] body = "Hello restEasy".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        client = newClient();

        try (Response response = client.target(baseUri())
                .path("hello")
                .queryParam("name", "restEasy")
                .queryParam("lang", "java")
                .request(MediaType.TEXT_PLAIN_TYPE)
                .get()) {

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getHeaderString("X-Test-Header")).isEqualTo("ok");
            assertThat(response.getMediaType().toString()).isEqualTo("text/plain");
            assertThat(response.readEntity(String.class)).isEqualTo("Hello restEasy");
        }
    }

    @Test
    void shouldSendPostEntityAndCustomHeaders() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/echo", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(exchange.getRequestHeaders().getFirst("X-Custom-Header")).isEqualTo("sent");
            assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).contains("text/plain");

            String requestBody = readRequestBody(exchange.getRequestBody());
            assertThat(requestBody).isEqualTo("payload");

            byte[] responseBody = ("received:" + requestBody).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(201, responseBody.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBody);
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        client = newClient();

        try (Response response = client.target(baseUri())
                .path("echo")
                .request()
                .header("X-Custom-Header", "sent")
                .post(Entity.entity("payload", MediaType.TEXT_PLAIN_TYPE))) {

            assertThat(response.getStatus()).isEqualTo(201);
            assertThat(response.readEntity(String.class)).isEqualTo("received:payload");
        }
    }

    @Test
    void shouldSupportInvocationBuilderReuseForMultipleRequests() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/counter", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            byte[] body = ("query:" + query).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        client = newClient();

        Invocation.Builder firstBuilder = client.target(baseUri())
                .path("counter")
                .queryParam("n", 1)
                .request(MediaType.TEXT_PLAIN_TYPE);

        Invocation.Builder secondBuilder = client.target(baseUri())
                .path("counter")
                .queryParam("n", 2)
                .request(MediaType.TEXT_PLAIN_TYPE);

        String first = firstBuilder.get(String.class);
        String second = secondBuilder.get(String.class);

        assertThat(first).isEqualTo("query:n=1");
        assertThat(second).isEqualTo("query:n=2");
    }

    @Test
    void shouldHandleNoContentResponses() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/empty", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("DELETE");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        client = newClient();

        try (Response response = client.target(baseUri())
                .path("empty")
                .request()
                .delete()) {

            assertThat(response.getStatus()).isEqualTo(204);
            assertThat(response.hasEntity()).isFalse();
        }
    }

    @Test
    void shouldExposeErrorStatusAndBody() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/error", exchange -> {
            byte[] body = "bad request".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(400, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        client = newClient();

        try (Response response = client.target(baseUri())
                .path("error")
                .request()
                .get()) {

            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.readEntity(String.class)).isEqualTo("bad request");
        }
    }

    @Test
    void shouldExecuteAsyncRequest() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/async", exchange -> {
            byte[] body = "async-response".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        client = newClient();

        CompletionStage<Response> stage = client.target(baseUri())
                .path("async")
                .request()
                .rx()
                .get();

        try (Response response = get(stage)) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.readEntity(String.class)).isEqualTo("async-response");
        }
    }

    private String baseUri() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static String readRequestBody(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    private static Response get(CompletionStage<Response> stage)
            throws ExecutionException, InterruptedException, java.util.concurrent.TimeoutException {
        return stage.toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
}
