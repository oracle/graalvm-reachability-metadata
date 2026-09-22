/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_core_http_netty;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.http.ProxyOptions;
import com.azure.core.http.netty.NettyAsyncHttpClientBuilder;
import com.azure.core.http.netty.NettyAsyncHttpClientProvider;
import com.azure.core.util.BinaryData;
import com.azure.core.util.Configuration;
import com.azure.core.util.Context;
import com.azure.core.util.Contexts;
import com.azure.core.util.HttpClientOptions;
import com.azure.core.util.ProgressReporter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.netty.channel.nio.NioEventLoopGroup;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.netty.resources.ConnectionProvider;

@Timeout(60)
public class Azure_core_http_nettyTest {
    private static final Duration IO_TIMEOUT = Duration.ofSeconds(10);

    static {
        Configuration.getGlobalConfiguration().put(Configuration.PROPERTY_AZURE_LOG_LEVEL, "verbose");
    }

    @Test
    void builderSendsRequestAndStreamsResponse() throws Exception {
        try (TestHttpServer server = TestHttpServer.create(exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestURI().getPath()).isEqualTo("/greeting");
            assertThat(exchange.getRequestURI().getRawQuery()).isEqualTo("language=en");
            assertThat(exchange.getRequestHeaders().getFirst("X-Request-Id")).isEqualTo("request-123");
            exchange.getResponseHeaders().put("X-Result", List.of("first", "second"));
            writeResponse(exchange, 202, "hello from netty client");
        })) {
            HttpClient client = configuredBuilder().wiretap(true).build();
            HttpRequest request = new HttpRequest(HttpMethod.GET, server.url("/greeting?language=en"))
                    .setHeader(HttpHeaderName.fromString("X-Request-Id"), "request-123");

            try (HttpResponse response = client.send(request).block(IO_TIMEOUT)) {
                assertThat(response).isNotNull();
                assertThat(response.getStatusCode()).isEqualTo(202);
                assertThat(response.getRequest()).isSameAs(request);
                assertThat(response.getHeaderValue(HttpHeaderName.fromString("X-Result")))
                        .contains("first", "second");
                assertThat(response.getBodyAsString().block(IO_TIMEOUT)).isEqualTo("hello from netty client");
            }
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
            HttpClient client = new NettyAsyncHttpClientProvider().createInstance(clientOptions());
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
    void eagerlyReadResponseSupportsReplayableBodyRepresentations() throws Exception {
        byte[] expectedBody = "replayable response".getBytes(UTF_8);
        try (TestHttpServer server = TestHttpServer.create(exchange -> {
            exchange.getResponseHeaders().set("X-Response-Mode", "eager");
            writeResponse(exchange, 200, "replayable response");
        })) {
            HttpClient client = configuredBuilder().disableBufferCopy(true).build();
            HttpRequest request = new HttpRequest(HttpMethod.GET, server.url("/eager"));
            Context context = Context.NONE.addData("azure-eagerly-read-response", true);

            try (HttpResponse response = client.send(request, context).block(IO_TIMEOUT)) {
                assertThat(response).isNotNull();
                assertThat(response.getStatusCode()).isEqualTo(200);
                assertThat(response.getHeaderValue("X-Response-Mode")).isEqualTo("eager");
                assertThat(response.getBodyAsString(UTF_8).block(IO_TIMEOUT)).isEqualTo("replayable response");
                assertThat(response.getBodyAsBinaryData().toBytes()).containsExactly(expectedBody);
                assertThat(response.buffer()).isSameAs(response);

                ByteBuffer bodyBuffer = response.getBody().blockFirst(IO_TIMEOUT);
                assertThat(bodyBuffer).isNotNull();
                byte[] bodyFromFlux = new byte[bodyBuffer.remaining()];
                bodyBuffer.get(bodyFromFlux);
                assertThat(bodyFromFlux).containsExactly(expectedBody);

                ByteArrayOutputStream destination = new ByteArrayOutputStream();
                response.writeBodyTo(Channels.newChannel(destination));
                assertThat(destination.toByteArray()).containsExactly(expectedBody);
            }
        }
    }

    @Test
    void asynchronousUploadReportsStreamingProgress() throws Exception {
        byte[] firstChunk = "first".getBytes(UTF_8);
        byte[] secondChunk = "-second".getBytes(UTF_8);
        ConcurrentLinkedDeque<Long> uploadProgress = new ConcurrentLinkedDeque<>();
        try (TestHttpServer server = TestHttpServer.create(exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(exchange.getRequestBody().readAllBytes()).isEqualTo("first-second".getBytes(UTF_8));
            writeResponse(exchange, 200, "uploaded");
        })) {
            HttpClient client = configuredBuilder().build();
            HttpRequest request = new HttpRequest(HttpMethod.POST, server.url("/stream"))
                    .setHeader(HttpHeaderName.CONTENT_LENGTH, "12")
                    .setBody(Flux.just(ByteBuffer.wrap(firstChunk), ByteBuffer.wrap(secondChunk)));
            Context context = Contexts.with(Context.NONE)
                    .setHttpRequestProgressReporter(ProgressReporter.withProgressListener(uploadProgress::add))
                    .getContext();

            try (HttpResponse response = client.send(request, context).block(IO_TIMEOUT)) {
                assertThat(response).isNotNull();
                assertThat(response.getStatusCode()).isEqualTo(200);
                assertThat(response.getBodyAsString().block(IO_TIMEOUT)).isEqualTo("uploaded");
                assertThat(uploadProgress).isNotEmpty();
                assertThat(uploadProgress.getLast()).isEqualTo(12L);
            }
        }
    }

    @Test
    void uploadsFileBackedBinaryData(@TempDir Path tempDirectory) throws Exception {
        byte[] expectedBody = "file-backed request body".getBytes(UTF_8);
        Path uploadFile = Files.write(tempDirectory.resolve("request-body.txt"), expectedBody);
        try (TestHttpServer server = TestHttpServer.create(exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("PUT");
            assertThat(exchange.getRequestHeaders().getFirst("Content-Length"))
                    .isEqualTo(Integer.toString(expectedBody.length));
            assertThat(exchange.getRequestBody().readAllBytes()).isEqualTo(expectedBody);
            writeResponse(exchange, 200, "stored");
        })) {
            HttpClient client = configuredBuilder().build();
            HttpRequest request = new HttpRequest(HttpMethod.PUT, server.url("/files/request-body.txt"))
                    .setHeader(HttpHeaderName.CONTENT_LENGTH, Integer.toString(expectedBody.length))
                    .setBody(BinaryData.fromFile(uploadFile));

            try (HttpResponse response = client.send(request).block(IO_TIMEOUT)) {
                assertThat(response).isNotNull();
                assertThat(response.getStatusCode()).isEqualTo(200);
                assertThat(response.getBodyAsString().block(IO_TIMEOUT)).isEqualTo("stored");
            }
        }
    }

    @Test
    void defaultClientIsDiscoveredThroughHttpClientSpi() throws Exception {
        try (TestHttpServer server = TestHttpServer.create(exchange -> writeResponse(exchange, 200, "spi client"))) {
            HttpClient client = HttpClient.createDefault(clientOptions());
            HttpRequest request = new HttpRequest(HttpMethod.GET, server.url("/provider"));

            try (HttpResponse response = client.send(request).block(IO_TIMEOUT)) {
                assertThat(response).isNotNull();
                assertThat(response.getStatusCode()).isEqualTo(200);
                assertThat(response.getBodyAsString().block(IO_TIMEOUT)).isEqualTo("spi client");
            }
        }
    }

    @Test
    void nonProxyHostsBypassConfiguredProxy() throws Exception {
        AtomicInteger destinationRequests = new AtomicInteger();
        AtomicInteger proxyRequests = new AtomicInteger();
        try (TestHttpServer destination = TestHttpServer.create(exchange -> {
                    destinationRequests.incrementAndGet();
                    assertThat(exchange.getRequestURI().getPath()).isEqualTo("/direct");
                    writeResponse(exchange, 200, "direct response");
                });
                TestHttpServer proxy = TestHttpServer.create(exchange -> {
                    proxyRequests.incrementAndGet();
                    writeResponse(exchange, 502, "unexpected proxy response");
                })) {
            ProxyOptions proxyOptions = new ProxyOptions(
                            ProxyOptions.Type.HTTP,
                            new InetSocketAddress(InetAddress.getLoopbackAddress(), proxy.port()))
                    .setNonProxyHosts(destination.url("/").getHost());
            HttpClient client = configuredBuilder().proxy(proxyOptions).build();
            HttpRequest request = new HttpRequest(HttpMethod.GET, destination.url("/direct"));

            try (HttpResponse response = client.send(request).block(IO_TIMEOUT)) {
                assertThat(response).isNotNull();
                assertThat(response.getStatusCode()).isEqualTo(200);
                assertThat(response.getBodyAsString().block(IO_TIMEOUT)).isEqualTo("direct response");
                assertThat(destinationRequests).hasValue(1);
                assertThat(proxyRequests).hasValue(0);
            }
        }
    }

    @Test
    void connectionProviderReusesPooledConnection() throws Exception {
        Set<Integer> remotePorts = ConcurrentHashMap.newKeySet();
        ConnectionProvider connectionProvider = ConnectionProvider.builder("azure-netty-test-pool")
                .maxConnections(1)
                .pendingAcquireTimeout(IO_TIMEOUT)
                .build();
        try (TestHttpServer server = TestHttpServer.create(exchange -> {
            remotePorts.add(exchange.getRemoteAddress().getPort());
            writeResponse(exchange, 200, "pooled response");
        })) {
            HttpClient client = configuredBuilder().connectionProvider(connectionProvider).build();

            for (int requestNumber = 0; requestNumber < 2; requestNumber++) {
                HttpRequest request = new HttpRequest(HttpMethod.GET, server.url("/pooled/" + requestNumber));
                try (HttpResponse response = client.send(request).block(IO_TIMEOUT)) {
                    assertThat(response).isNotNull();
                    assertThat(response.getStatusCode()).isEqualTo(200);
                    assertThat(response.getBodyAsString().block(IO_TIMEOUT)).isEqualTo("pooled response");
                }
            }

            assertThat(remotePorts).hasSize(1);
        } finally {
            connectionProvider.disposeLater().block(IO_TIMEOUT);
        }
    }

    @Test
    void customEventLoopHandlesRequestAndShutsDown() throws Exception {
        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
        try (TestHttpServer server = TestHttpServer.create(exchange -> writeResponse(exchange, 200, "custom loop"))) {
            HttpClient client = configuredBuilder().nioEventLoopGroup(eventLoopGroup).build();
            HttpRequest request = new HttpRequest(HttpMethod.GET, server.url("/custom-loop"));

            try (HttpResponse response = client.send(request).block(IO_TIMEOUT)) {
                assertThat(response).isNotNull();
                assertThat(response.getStatusCode()).isEqualTo(200);
                assertThat(response.getBodyAsString().block(IO_TIMEOUT)).isEqualTo("custom loop");
            }
        } finally {
            assertThat(eventLoopGroup.shutdownGracefully(0, 10, TimeUnit.SECONDS).await(10, TimeUnit.SECONDS))
                    .isTrue();
            assertThat(eventLoopGroup.isTerminated()).isTrue();
        }
    }

    private static NettyAsyncHttpClientBuilder configuredBuilder() {
        return new NettyAsyncHttpClientBuilder()
                .configuration(Configuration.NONE)
                .connectTimeout(IO_TIMEOUT)
                .writeTimeout(IO_TIMEOUT)
                .responseTimeout(IO_TIMEOUT)
                .readTimeout(IO_TIMEOUT);
    }

    private static HttpClientOptions clientOptions() {
        return new HttpClientOptions()
                .setConfiguration(Configuration.NONE)
                .setConnectTimeout(IO_TIMEOUT)
                .setWriteTimeout(IO_TIMEOUT)
                .setResponseTimeout(IO_TIMEOUT)
                .setReadTimeout(IO_TIMEOUT);
    }

    private static ExecutorService newDaemonExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "azure-netty-test-server");
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
            ExecutorService executor = newDaemonExecutor();
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
