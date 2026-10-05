/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j_http_client_jdk;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.http.client.sse.HttpResponseReceived;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class Langchain4j_http_client_jdkTest {
    private static final Duration IO_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void synchronousExecutionBuildsRequestAndReadsResponse() throws Exception {
        AtomicReference<RequestSnapshot> received = new AtomicReference<>();
        try (TestHttpServer server = TestHttpServer.create(exchange -> {
                    received.set(RequestSnapshot.capture(exchange));
                    writeResponse(exchange, 202, "hello from jdk client", "text/plain; charset=UTF-8");
                });
                TestClient client = TestClient.create()) {
            HttpRequest request = HttpRequest.builder()
                    .method(HttpMethod.GET)
                    .url(server.url("/greeting"))
                    .addQueryParam("language", "en us")
                    .addHeader("X-Request-Id", "request-123")
                    .build();

            SuccessfulHttpResponse response = client.client().execute(request);

            assertThat(response.statusCode()).isEqualTo(202);
            assertThat(response.contentType()).isEqualTo("text/plain; charset=UTF-8");
            assertThat(response.body()).isEqualTo("hello from jdk client");
            assertThat(response.headers()).containsKey("X-Result");

            RequestSnapshot requestSnapshot = received.get();
            assertThat(requestSnapshot.method()).isEqualTo("GET");
            assertThat(requestSnapshot.path()).isEqualTo("/greeting");
            assertThat(requestSnapshot.query()).isEqualTo("language=en+us");
            assertThat(requestSnapshot.header("X-Request-Id")).isEqualTo("request-123");
            assertThat(requestSnapshot.body()).isEmpty();
        }
    }

    @Test
    void synchronousExecutionSendsPlainAndMultipartRequestBodies() throws Exception {
        List<RequestSnapshot> received = new CopyOnWriteArrayList<>();
        try (TestHttpServer server = TestHttpServer.create(exchange -> {
                    received.add(RequestSnapshot.capture(exchange));
                    writeResponse(exchange, 200, "accepted", "text/plain");
                });
                TestClient client = TestClient.create()) {
            SuccessfulHttpResponse plainResponse = client.client().execute(HttpRequest.builder()
                    .method(HttpMethod.POST)
                    .url(server.url("/json"))
                    .addHeader("Content-Type", "application/json")
                    .body("{\"message\":\"hello\"}")
                    .build());
            SuccessfulHttpResponse multipartResponse = client.client().execute(HttpRequest.builder()
                    .method(HttpMethod.POST)
                    .url(server.url("/upload"))
                    .addFormDataField("prompt", "describe this file")
                    .addFormDataFile("document", "notes.txt", "text/plain", "file contents".getBytes(UTF_8))
                    .build());

            assertThat(plainResponse.body()).isEqualTo("accepted");
            assertThat(multipartResponse.body()).isEqualTo("accepted");
            assertThat(received).hasSize(2);
            assertThat(received.get(0).method()).isEqualTo("POST");
            assertThat(received.get(0).path()).isEqualTo("/json");
            assertThat(received.get(0).header("Content-Type")).isEqualTo("application/json");
            assertThat(received.get(0).body()).isEqualTo("{\"message\":\"hello\"}");

            RequestSnapshot multipart = received.get(1);
            assertThat(multipart.path()).isEqualTo("/upload");
            assertThat(multipart.header("Content-Type"))
                    .isEqualTo("multipart/form-data; boundary=----LangChain4j");
            assertThat(multipart.body())
                    .contains("name=\"prompt\"", "describe this file", "name=\"document\"; filename=\"notes.txt\"")
                    .contains("Content-Type: text/plain", "file contents", "------LangChain4j--");
        }
    }

    @Test
    void synchronousAndAsynchronousExecutionExposeHttpFailures() throws Exception {
        try (TestHttpServer server = TestHttpServer.create(exchange ->
                        writeResponse(exchange, 429, "rate limited", "text/plain"));
                TestClient client = TestClient.create()) {
            HttpRequest request = HttpRequest.builder()
                    .method(HttpMethod.GET)
                    .url(server.url("/limited"))
                    .build();

            HttpException synchronousFailure = null;
            try {
                client.client().execute(request);
                fail("The synchronous request should report the HTTP failure");
            } catch (HttpException exception) {
                synchronousFailure = exception;
            }
            assertThat(synchronousFailure).isNotNull();
            assertThat(synchronousFailure.statusCode()).isEqualTo(429);
            assertThat(synchronousFailure.getMessage()).isEqualTo("rate limited");

            try {
                client.client().executeAsync(request).get(IO_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                fail("The asynchronous request should report the HTTP failure");
            } catch (ExecutionException exception) {
                assertThat(exception.getCause()).isInstanceOf(HttpException.class);
                HttpException asynchronousFailure = (HttpException) exception.getCause();
                assertThat(asynchronousFailure.statusCode()).isEqualTo(429);
                assertThat(asynchronousFailure.getMessage()).isEqualTo("rate limited");
            }
        }
    }

    @Test
    void serviceProviderBuildsClientAndExecutesAsynchronousRequest() throws Exception {
        AtomicReference<RequestSnapshot> received = new AtomicReference<>();
        try (TestHttpServer server = TestHttpServer.create(exchange -> {
                    received.set(RequestSnapshot.capture(exchange));
                    writeResponse(exchange, 200, "loaded through spi", "text/plain");
                });
                TestClient client = TestClient.createFromServiceProvider()) {
            SuccessfulHttpResponse response = client.client().executeAsync(HttpRequest.builder()
                            .method(HttpMethod.GET)
                            .url(server.url("/spi"))
                            .build())
                    .get(IO_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("loaded through spi");
            assertThat(received.get().path()).isEqualTo("/spi");
        }
    }

    @Test
    void callbackExecutionParsesServerSentEventsAndClosesStream() throws Exception {
        String events = "event: message\n" + "data: first\n" + "data: second\n\n" + "data: done\n\n";
        try (TestHttpServer server = TestHttpServer.create(exchange ->
                        writeResponse(exchange, 200, events, "text/event-stream"));
                TestClient client = TestClient.create()) {
            AtomicReference<SuccessfulHttpResponse> opened = new AtomicReference<>();
            List<ServerSentEvent> received = new CopyOnWriteArrayList<>();
            AtomicReference<ServerSentEventContext> context = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            CountDownLatch closed = new CountDownLatch(1);

            client.client().execute(
                    HttpRequest.builder().method(HttpMethod.GET).url(server.url("/events")).build(),
                    new ServerSentEventListener() {
                        @Override
                        public void onOpen(SuccessfulHttpResponse response) {
                            opened.set(response);
                        }

                        @Override
                        public void onEvent(ServerSentEvent event, ServerSentEventContext eventContext) {
                            received.add(event);
                            context.set(eventContext);
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            failure.set(throwable);
                            closed.countDown();
                        }

                        @Override
                        public void onClose() {
                            closed.countDown();
                        }
                    });

            assertThat(closed.await(IO_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            assertThat(failure.get()).isNull();
            assertThat(opened.get().statusCode()).isEqualTo(200);
            assertThat(context.get()).isNotNull();
            assertThat(received).containsExactly(
                    new ServerSentEvent("message", "first\nsecond"), new ServerSentEvent(null, "done"));
        }
    }

    @Test
    void publisherExecutionEmitsResponseAndServerSentEvents() throws Exception {
        String events = "event: token\n" + "data: one\n\n" + "event: token\n" + "data: two\n\n";
        try (TestHttpServer server = TestHttpServer.create(exchange ->
                        writeResponse(exchange, 200, events, "text/event-stream"));
                TestClient client = TestClient.createWithStreamingBuffer(8)) {
            List<HttpStreamingEvent> received = new CopyOnWriteArrayList<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            CountDownLatch completed = new CountDownLatch(1);

            client.client()
                    .stream(HttpRequest.builder().method(HttpMethod.GET).url(server.url("/publisher-events")).build())
                    .subscribe(new Flow.Subscriber<>() {
                        @Override
                        public void onSubscribe(Flow.Subscription subscription) {
                            subscription.request(Long.MAX_VALUE);
                        }

                        @Override
                        public void onNext(HttpStreamingEvent event) {
                            received.add(event);
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            failure.set(throwable);
                            completed.countDown();
                        }

                        @Override
                        public void onComplete() {
                            completed.countDown();
                        }
                    });

            assertThat(completed.await(IO_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            assertThat(failure.get()).isNull();
            assertThat(received).hasSize(3);
            assertThat(received.get(0)).isInstanceOf(HttpResponseReceived.class);
            assertThat(((HttpResponseReceived) received.get(0)).response().statusCode()).isEqualTo(200);
            assertThat(received.subList(1, 3))
                    .containsExactly(new ServerSentEvent("token", "one"), new ServerSentEvent("token", "two"));
        }
    }

    @Test
    void publisherExecutionBuffersEventsUntilSubscriberRequestsThem() throws Exception {
        String events = "event: token\n" + "data: one\n\n" + "event: token\n" + "data: two\n\n";
        CountDownLatch responseWritten = new CountDownLatch(1);
        try (TestHttpServer server = TestHttpServer.create(exchange -> {
                    writeResponse(exchange, 200, events, "text/event-stream");
                    responseWritten.countDown();
                });
                TestClient client = TestClient.createWithStreamingBuffer(3)) {
            List<HttpStreamingEvent> received = new CopyOnWriteArrayList<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<Flow.Subscription> subscriptionReference = new AtomicReference<>();

            client.client()
                    .stream(HttpRequest.builder().method(HttpMethod.GET).url(server.url("/backpressure")).build())
                    .subscribe(new Flow.Subscriber<>() {
                        @Override
                        public void onSubscribe(Flow.Subscription subscription) {
                            subscriptionReference.set(subscription);
                        }

                        @Override
                        public void onNext(HttpStreamingEvent event) {
                            received.add(event);
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            failure.set(throwable);
                            completed.countDown();
                        }

                        @Override
                        public void onComplete() {
                            completed.countDown();
                        }
                    });

            assertThat(responseWritten.await(IO_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            assertThat(received).isEmpty();
            assertThat(subscriptionReference.get()).isNotNull();
            subscriptionReference.get().request(3);

            assertThat(completed.await(IO_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            assertThat(failure.get()).isNull();
            assertThat(received).hasSize(3);
            assertThat(received.get(0)).isInstanceOf(HttpResponseReceived.class);
            assertThat(((HttpResponseReceived) received.get(0)).response().statusCode()).isEqualTo(200);
            assertThat(received.subList(1, 3))
                    .containsExactly(new ServerSentEvent("token", "one"), new ServerSentEvent("token", "two"));
        }
    }

    private static void writeResponse(HttpExchange exchange, int statusCode, String body, String contentType)
            throws IOException {
        byte[] bytes = body.getBytes(UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        if (statusCode == 202) {
            exchange.getResponseHeaders().set("X-Result", "accepted");
        }
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static ExecutorService newDaemonExecutor(String name) {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        });
    }

    private record RequestSnapshot(
            String method, String path, String query, String requestId, String contentType, String body) {
        static RequestSnapshot capture(HttpExchange exchange) throws IOException {
            return new RequestSnapshot(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getRawQuery(),
                    exchange.getRequestHeaders().getFirst("X-Request-Id"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), UTF_8));
        }

        String header(String name) {
            if ("X-Request-Id".equalsIgnoreCase(name)) {
                return requestId;
            }
            if ("Content-Type".equalsIgnoreCase(name)) {
                return contentType;
            }
            return null;
        }
    }

    private static final class TestClient implements AutoCloseable {
        private final HttpClient client;
        private final ExecutorService executor;

        private TestClient(HttpClient client, ExecutorService executor) {
            this.client = client;
            this.executor = executor;
        }

        static TestClient create() {
            return create(JdkHttpClient.builder());
        }

        static TestClient createWithStreamingBuffer(int bufferSize) {
            return create(JdkHttpClient.builder().streamingBufferSize(bufferSize));
        }

        static TestClient createFromServiceProvider() {
            JdkHttpClientBuilder builder = (JdkHttpClientBuilder) HttpClientBuilderLoader.loadHttpClientBuilder();
            return create(builder);
        }

        private static TestClient create(JdkHttpClientBuilder builder) {
            ExecutorService executor = newDaemonExecutor("langchain4j-jdk-client");
            JdkHttpClient client = builder
                    .httpClientBuilder(java.net.http.HttpClient.newBuilder().executor(executor))
                    .connectTimeout(IO_TIMEOUT)
                    .readTimeout(IO_TIMEOUT)
                    .build();
            return new TestClient(client, executor);
        }

        HttpClient client() {
            return client;
        }

        @Override
        public void close() throws InterruptedException {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(IO_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
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
            ExecutorService executor = newDaemonExecutor("langchain4j-jdk-test-server");
            server.createContext("/", exchange -> {
                try (exchange) {
                    handler.handle(exchange);
                }
            });
            server.setExecutor(executor);
            server.start();
            return new TestHttpServer(server, executor);
        }

        String url(String path) {
            return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + path;
        }

        @Override
        public void close() throws InterruptedException {
            server.stop(0);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(IO_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        }
    }
}
