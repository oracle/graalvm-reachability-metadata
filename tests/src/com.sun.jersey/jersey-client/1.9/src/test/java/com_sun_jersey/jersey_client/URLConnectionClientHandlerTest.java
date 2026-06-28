/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_client;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.ClientFilter;
import com.sun.jersey.client.urlconnection.URLConnectionClientHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class URLConnectionClientHandlerTest {
    private static final int CLIENT_TIMEOUT_MILLIS = 10_000;

    @Test
    void defaultUrlConnectionHandlerSendsRequestsThroughLocalHttpServer() throws Exception {
        try (TestHttpServer server = TestHttpServer.start(URLConnectionClientHandlerTest::handleEchoRequest)) {
            Client client = createClient();
            client.addFilter(new ClientFilter() {
                @Override
                public ClientResponse handle(ClientRequest request) {
                    request.getHeaders().putSingle("X-Filter", "filter-added");
                    return getNext().handle(request);
                }
            });

            try {
                ClientResponse getResponse = client.resource(server.url("/echo"))
                        .queryParam("q", "jersey")
                        .header("X-Client", "get-test")
                        .accept("text/plain")
                        .get(ClientResponse.class);
                try {
                    assertThat(getResponse.getStatus()).isEqualTo(200);
                    assertThat(getResponse.getEntity(String.class))
                            .contains("method=GET")
                            .contains("uri=/echo?q=jersey")
                            .contains("client=get-test")
                            .contains("filter=filter-added");
                } finally {
                    getResponse.close();
                }

                ClientResponse postResponse = client.resource(server.url("/form"))
                        .type("application/x-www-form-urlencoded")
                        .header("X-Client", "post-test")
                        .post(ClientResponse.class, "name=jersey&mode=native");
                try {
                    assertThat(postResponse.getStatus()).isEqualTo(201);
                    assertThat(postResponse.getEntity(String.class))
                            .contains("method=POST")
                            .contains("contentType=application/x-www-form-urlencoded")
                            .contains("body=name=jersey&mode=native")
                            .contains("filter=filter-added");
                } finally {
                    postResponse.close();
                }
            } finally {
                client.destroy();
            }
        }
    }

    @Test
    void methodWorkaroundAllowsUnsupportedHttpUrlConnectionMethods() throws Exception {
        try (TestHttpServer server = TestHttpServer.start(URLConnectionClientHandlerTest::handleWebDavRequest)) {
            Client client = createClient();
            client.getProperties().put(
                    URLConnectionClientHandler.PROPERTY_HTTP_URL_CONNECTION_SET_METHOD_WORKAROUND,
                    Boolean.TRUE);

            try {
                ClientResponse response = client.resource(server.url("/webdav"))
                        .header("Depth", "0")
                        .method("PROPFIND", ClientResponse.class);
                try {
                    assertThat(response.getStatus()).isEqualTo(207);
                    assertThat(response.getEntity(String.class))
                            .contains("method=PROPFIND")
                            .contains("depth=0");
                } finally {
                    response.close();
                }
            } finally {
                client.destroy();
            }
        }
    }

    private static Client createClient() {
        Client client = Client.create();
        client.setConnectTimeout(CLIENT_TIMEOUT_MILLIS);
        client.setReadTimeout(CLIENT_TIMEOUT_MILLIS);
        return client;
    }

    private static void handleEchoRequest(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String response = "method=" + exchange.getRequestMethod()
                + "\nuri=" + exchange.getRequestURI()
                + "\nclient=" + exchange.getRequestHeaders().getFirst("X-Client")
                + "\nfilter=" + exchange.getRequestHeaders().getFirst("X-Filter")
                + "\ncontentType=" + exchange.getRequestHeaders().getFirst("Content-Type")
                + "\nbody=" + body;
        int status = "POST".equals(exchange.getRequestMethod()) ? 201 : 200;
        sendText(exchange, status, response);
    }

    private static void handleWebDavRequest(HttpExchange exchange) throws IOException {
        String response = "method=" + exchange.getRequestMethod()
                + "\ndepth=" + exchange.getRequestHeaders().getFirst("Depth");
        sendText(exchange, 207, response);
    }

    private static void sendText(HttpExchange exchange, int status, String response) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(status, responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.close();
    }

    private static final class TestHttpServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;

        private TestHttpServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        private static TestHttpServer start(HttpHandler handler) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            server.createContext("/", handler);
            server.setExecutor(executor);
            server.start();
            return new TestHttpServer(server, executor);
        }

        private String url(String path) {
            return "http://127.0.0.1:" + server.getAddress().getPort() + path;
        }

        @Override
        public void close() throws InterruptedException {
            server.stop(0);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
