/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_core_http_jdk_httpclient;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.http.ProxyOptions;
import com.azure.core.http.jdk.httpclient.JdkHttpClientBuilder;
import com.azure.core.http.jdk.httpclient.JdkHttpClientProvider;
import com.azure.core.util.Configuration;
import com.azure.core.util.Context;
import com.azure.core.util.HttpClientOptions;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class Azure_core_http_jdk_httpclientTest {
    private static final Duration IO_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void builderSendsAsynchronousRequestAndStreamsResponse() throws Exception {
        ExecutorService clientExecutor = newDaemonExecutor("azure-jdk-client");
        try (TestHttpServer server = TestHttpServer.create(exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestURI().getPath()).isEqualTo("/greeting");
            assertThat(exchange.getRequestURI().getRawQuery()).isEqualTo("language=en");
            assertThat(exchange.getRequestHeaders().getFirst("X-Request-Id")).isEqualTo("request-123");
            exchange.getResponseHeaders().put("X-Result", List.of("first", "second"));
            writeResponse(exchange, 202, "hello from jdk client");
        })) {
            HttpClient client = new JdkHttpClientBuilder()
                    .executor(clientExecutor)
                    .configuration(Configuration.NONE)
                    .connectionTimeout(IO_TIMEOUT)
                    .writeTimeout(IO_TIMEOUT)
                    .responseTimeout(IO_TIMEOUT)
                    .readTimeout(IO_TIMEOUT)
                    .build();
            HttpRequest request = new HttpRequest(HttpMethod.GET, server.url("/greeting?language=en"))
                    .setHeader(HttpHeaderName.fromString("X-Request-Id"), "request-123");

            try (HttpResponse response = client.send(request).block(IO_TIMEOUT)) {
                assertThat(response).isNotNull();
                assertThat(response.getStatusCode()).isEqualTo(202);
                assertThat(response.getRequest()).isSameAs(request);
                assertThat(response.getHeaderValue(HttpHeaderName.fromString("X-Result")))
                        .contains("first", "second");
                assertThat(response.getBodyAsString().block(IO_TIMEOUT)).isEqualTo("hello from jdk client");
            }
        } finally {
            shutdown(clientExecutor);
        }
    }

    @Test
    void providerSendsSynchronousRequestBodyAndReadsResponse() throws Exception {
        try (TestHttpServer server = TestHttpServer.create(exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(exchange.getRequestURI().getPath()).isEqualTo("/items");
            assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).isEqualTo("text/plain; charset=utf-8");
            assertThat(new String(exchange.getRequestBody().readAllBytes(), UTF_8)).isEqualTo("azure request body");
            writeResponse(exchange, 201, "created");
        })) {
            HttpClientOptions options = new HttpClientOptions()
                    .setConfiguration(Configuration.NONE)
                    .setConnectTimeout(IO_TIMEOUT)
                    .setWriteTimeout(IO_TIMEOUT)
                    .setResponseTimeout(IO_TIMEOUT)
                    .setReadTimeout(IO_TIMEOUT);
            HttpClient client = new JdkHttpClientProvider().createInstance(options);
            HttpRequest request = new HttpRequest(HttpMethod.POST, server.url("/items"))
                    .setHeader(HttpHeaderName.CONTENT_TYPE, "text/plain; charset=utf-8")
                    .setBody("azure request body");

            try (HttpResponse response = client.sendSync(request, Context.NONE)) {
                assertThat(response.getStatusCode()).isEqualTo(201);
                assertThat(response.getHeaders().getValue(HttpHeaderName.CONTENT_LENGTH)).isEqualTo("7");
                assertThat(response.getBodyAsByteArray().block(IO_TIMEOUT)).containsExactly("created".getBytes(UTF_8));
            }
        }
    }

    @Test
    void defaultClientIsDiscoveredThroughHttpClientSpi() throws Exception {
        try (TestHttpServer server = TestHttpServer.create(exchange -> writeResponse(exchange, 200, "spi client"))) {
            HttpClientOptions options = new HttpClientOptions()
                    .setConfiguration(Configuration.NONE)
                    .setConnectTimeout(IO_TIMEOUT)
                    .setWriteTimeout(IO_TIMEOUT)
                    .setResponseTimeout(IO_TIMEOUT)
                    .setReadTimeout(IO_TIMEOUT);
            HttpClient client = HttpClient.createDefault(options);
            HttpRequest request = new HttpRequest(HttpMethod.GET, server.url("/provider"));

            try (HttpResponse response = client.send(request).block(IO_TIMEOUT)) {
                assertThat(response).isNotNull();
                assertThat(response.getStatusCode()).isEqualTo(200);
                assertThat(response.getBodyAsString().block(IO_TIMEOUT)).isEqualTo("spi client");
            }
        }
    }

    @Test
    void configuredProxyRoutesRequestToProxyServer() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        try (TestHttpServer proxy = TestHttpServer.create(exchange -> {
            requests.incrementAndGet();
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestURI().toString()).isEqualTo("http://azure.example/proxied?source=test");
            assertThat(exchange.getRequestHeaders().getFirst("Host")).isEqualTo("azure.example");
            writeResponse(exchange, 200, "response from proxy");
        })) {
            ProxyOptions proxyOptions = new ProxyOptions(
                    ProxyOptions.Type.HTTP, new InetSocketAddress(InetAddress.getLoopbackAddress(), proxy.port()));
            HttpClient client = new JdkHttpClientBuilder()
                    .configuration(Configuration.NONE)
                    .proxy(proxyOptions)
                    .connectionTimeout(IO_TIMEOUT)
                    .writeTimeout(IO_TIMEOUT)
                    .responseTimeout(IO_TIMEOUT)
                    .readTimeout(IO_TIMEOUT)
                    .build();
            HttpRequest request = new HttpRequest(HttpMethod.GET, new URL("http://azure.example/proxied?source=test"));

            try (HttpResponse response = client.send(request).block(IO_TIMEOUT)) {
                assertThat(response).isNotNull();
                assertThat(response.getStatusCode()).isEqualTo(200);
                assertThat(response.getBodyAsString().block(IO_TIMEOUT)).isEqualTo("response from proxy");
                assertThat(requests).hasValue(1);
            }
        }
    }

    private static ExecutorService newDaemonExecutor(String threadName) {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    private static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    private static void writeResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream responseBody = exchange.getResponseBody()) {
            responseBody.write(bytes);
        }
    }

    private static final class TestHttpServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;

        private TestHttpServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static TestHttpServer create(HttpHandler handler) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            ExecutorService executor = newDaemonExecutor("azure-jdk-test-server");
            server.createContext("/", exchange -> {
                try (exchange) {
                    handler.handle(exchange);
                }
            });
            server.setExecutor(executor);
            server.start();
            return new TestHttpServer(server, executor);
        }

        URL url(String path) throws IOException {
            return new URL("http", server.getAddress().getHostString(), port(), path);
        }

        int port() {
            return server.getAddress().getPort();
        }

        @Override
        public void close() throws InterruptedException {
            server.stop(0);
            shutdown(executor);
        }
    }
}
