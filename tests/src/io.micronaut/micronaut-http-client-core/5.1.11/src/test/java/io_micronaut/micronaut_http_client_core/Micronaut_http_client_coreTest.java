/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_http_client_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.client.AsyncHttpClient;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.http.sse.Event;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class Micronaut_http_client_coreTest {
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    @Test
    @Timeout(55)
    void exchangesRequestsThroughTheImperativeAndBlockingClientApis() throws IOException {
        try (TestServer server = TestServer.start()) {
            DefaultHttpClientConfiguration configuration = clientConfiguration();
            try (HttpClient client = HttpClient.create(server.baseUri().toURL(), configuration)) {
                BlockingHttpClient blockingClient = client.toBlocking();
                HttpRequest<String> request = HttpRequest.POST("/echo?mode=full", "native request")
                        .header("X-Request-Id", "request-17")
                        .contentType(MediaType.TEXT_PLAIN_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE);

                HttpResponse<String> response = blockingClient.exchange(request, String.class);

                assertThat(response.code()).isEqualTo(HttpStatus.OK.getCode());
                assertThat(response.getContentType()).contains(MediaType.TEXT_PLAIN_TYPE);
                assertThat(response.body())
                        .isEqualTo("POST|/echo|mode=full|request-17|none|native request");
            }
        }
    }

    @Test
    @Timeout(55)
    void retrievesResponsesThroughTheCompletionStageClientApi() throws Exception {
        try (TestServer server = TestServer.start()) {
            DefaultHttpClientConfiguration configuration = clientConfiguration();
            try (AsyncHttpClient client =
                    HttpClient.create(server.baseUri().toURL(), configuration).toAsync()) {
                String response = client.retrieve(HttpRequest.GET("/async?source=completion-stage"))
                        .toCompletableFuture()
                        .get(20, TimeUnit.SECONDS);

                assertThat(response)
                        .isEqualTo("GET|/async|source=completion-stage|none|none|");
            }
        }
    }

    @Test
    @Timeout(55)
    void streamsResponseBytesThroughTheStreamingClientFactory() throws Exception {
        try (TestServer server = TestServer.start();
                StreamingHttpClient client =
                        StreamingHttpClient.create(server.baseUri().toURL(), clientConfiguration())) {
            String response = awaitFirst(
                    client.dataStream(HttpRequest.GET("/stream?mode=bytes")),
                    buffer -> buffer.toString(StandardCharsets.UTF_8));

            assertThat(response).isEqualTo("GET|/stream|mode=bytes|none|none|");
        }
    }

    @Test
    @Timeout(55)
    void consumesServerSentEventsWithProtocolFields() throws Exception {
        try (TestServer server = TestServer.start()) {
            DefaultHttpClientConfiguration configuration = clientConfiguration();
            SseClient sseClient = SseClient.create(server.baseUri().toURL(), configuration);
            try (HttpClient client = (HttpClient) sseClient) {
                Event<String> event = awaitFirst(sseClient.eventStream("/events", String.class));

                assertThat(event.getData()).isEqualTo("available");
                assertThat(event.getId()).isEqualTo("event-7");
                assertThat(event.getName()).isEqualTo("inventory");
                assertThat(event.getRetry()).isEqualTo(Duration.ofMillis(1500));
            }
        }
    }

    @Test
    @Timeout(55)
    void resolvesADeclarativeClientAndAppliesItsRequestFilter() throws IOException {
        try (TestServer server = TestServer.start()) {
            Map<String, Object> properties = Map.ofEntries(
                    Map.entry("micronaut.http.services.catalog.url", server.baseUri()),
                    Map.entry("micronaut.http.services.catalog.connect-timeout", HTTP_TIMEOUT),
                    Map.entry("micronaut.http.services.catalog.read-timeout", HTTP_TIMEOUT),
                    Map.entry("micronaut.http.services.catalog.request-timeout", HTTP_TIMEOUT));

            try (ApplicationContext context = ApplicationContext.run(properties)) {
                CatalogClient client = context.getBean(CatalogClient.class);

                String response = client.find(42, "summary", "integration-test");

                assertThat(response)
                        .isEqualTo("GET|/catalog/42|view=summary|integration-test|core-filter|");
            }
        }
    }

    private static DefaultHttpClientConfiguration clientConfiguration() {
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
        configuration.setConnectTimeout(HTTP_TIMEOUT);
        configuration.setReadTimeout(HTTP_TIMEOUT);
        configuration.setRequestTimeout(HTTP_TIMEOUT);
        return configuration;
    }

    private static <T> T awaitFirst(Publisher<T> publisher) throws Exception {
        return awaitFirst(publisher, Function.identity());
    }

    private static <T, R> R awaitFirst(Publisher<T> publisher, Function<? super T, ? extends R> mapper)
            throws Exception {
        FirstItemSubscriber<T, R> subscriber = new FirstItemSubscriber<>(mapper);
        publisher.subscribe(subscriber);
        return subscriber.result.get(20, TimeUnit.SECONDS);
    }

    @Client("catalog")
    public interface CatalogClient {
        @Get(uri = "/catalog/{id}", produces = MediaType.TEXT_PLAIN)
        String find(
                @PathVariable("id") long id,
                @QueryValue("view") String view,
                @Header("X-Caller") String caller);
    }

    @ClientFilter(value = "/catalog/**", serviceId = "catalog")
    public static final class CatalogClientFilter {
        @RequestFilter
        public void addFilterHeader(MutableHttpRequest<?> request) {
            request.header("X-Client-Filter", "core-filter");
        }
    }

    private static final class TestServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;

        private TestServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static TestServer start() throws IOException {
            InetAddress loopback = InetAddress.getLoopbackAddress();
            HttpServer server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            server.setExecutor(executor);
            server.createContext("/", TestServer::respond);
            server.start();
            return new TestServer(server, executor);
        }

        URI baseUri() {
            String host = server.getAddress().getAddress().getHostAddress();
            String uriHost = host.contains(":") ? "[" + host + "]" : host;
            return URI.create("http://" + uriHost + ":" + server.getAddress().getPort());
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }

        private static void respond(HttpExchange exchange) throws IOException {
            try (exchange) {
                if (exchange.getRequestURI().getPath().equals("/events")) {
                    String eventPayload = """
                            id: event-7
                            event: inventory
                            retry: 1500
                            data: available

                            """;
                    byte[] eventBytes = eventPayload.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                    exchange.sendResponseHeaders(HttpStatus.OK.getCode(), eventBytes.length);
                    exchange.getResponseBody().write(eventBytes);
                    return;
                }

                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String response = String.join(
                        "|",
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(),
                        String.valueOf(exchange.getRequestURI().getRawQuery()),
                        header(exchange, "X-Request-Id", header(exchange, "X-Caller", "none")),
                        header(exchange, "X-Client-Filter", "none"),
                        body);
                byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", MediaType.TEXT_PLAIN);
                exchange.sendResponseHeaders(HttpStatus.OK.getCode(), responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
            }
        }

        private static String header(HttpExchange exchange, String name, String fallback) {
            String value = exchange.getRequestHeaders().getFirst(name);
            return value == null ? fallback : value;
        }
    }

    private static final class FirstItemSubscriber<T, R> implements Subscriber<T> {
        private final CompletableFuture<R> result = new CompletableFuture<>();
        private final Function<? super T, ? extends R> mapper;
        private Subscription subscription;

        private FirstItemSubscriber(Function<? super T, ? extends R> mapper) {
            this.mapper = mapper;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(T item) {
            try {
                result.complete(mapper.apply(item));
            } catch (RuntimeException exception) {
                result.completeExceptionally(exception);
            } finally {
                subscription.cancel();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            result.completeExceptionally(new IllegalStateException("SSE stream completed without an event"));
        }
    }
}
