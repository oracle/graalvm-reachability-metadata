/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_zipkin_reporter2.zipkin_sender_okhttp3;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.junit.jupiter.api.Test;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Call;
import zipkin2.reporter.Callback;
import zipkin2.reporter.CheckResult;
import zipkin2.reporter.Encoding;
import zipkin2.reporter.okhttp3.OkHttpSender;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Zipkin_sender_okhttp3Test {

    private static final Span FIRST_SPAN = span(
            "463ac35c9f6413ad",
            "a2fb4a1d1a96d312",
            "get-customer",
            "customers");
    private static final Span SECOND_SPAN = span(
            "463ac35c9f6413ae",
            "b7ad6b7169203331",
            "post-order",
            "orders");

    @Test
    void senderPostsJsonSpansWithDefaultGzipCompressionAndB3SuppressionHeader() throws Exception {
        try (TestCollector collector = new TestCollector()) {
            OkHttpSender sender = OkHttpSender.create(collector.endpoint());
            try {
                assertThat(sender.encoding()).isEqualTo(Encoding.JSON);
                assertThat(sender.messageMaxBytes()).isEqualTo(500_000);
                assertThat(sender.toString()).isEqualTo("OkHttpSender{" + collector.endpoint() + "}");

                sender.sendSpans(jsonSpans(FIRST_SPAN, SECOND_SPAN)).execute();

                CapturedRequest request = collector.takeRequest();
                assertThat(request.method()).isEqualTo("POST");
                assertThat(request.path()).isEqualTo("/api/v2/spans");
                assertThat(request.header("b3")).isEqualTo("0");
                assertThat(request.header("Content-Type")).isEqualTo("application/json");
                assertThat(request.header("Content-Encoding")).isEqualTo("gzip");
                assertThat(SpanBytesDecoder.JSON_V2.decodeList(request.bodyAfterGzipIfNeeded()))
                        .containsExactly(FIRST_SPAN, SECOND_SPAN);
            } finally {
                sender.close();
            }
        }
    }

    @Test
    void senderCanDisableCompressionAndReportMessageSizes() throws Exception {
        try (TestCollector collector = new TestCollector()) {
            OkHttpSender sender = OkHttpSender.newBuilder()
                    .endpoint(collector.endpoint())
                    .compressionEnabled(false)
                    .messageMaxBytes(1024)
                    .connectTimeout(250)
                    .readTimeout(250)
                    .writeTimeout(250)
                    .build();
            try {
                List<byte[]> encodedSpans = jsonSpans(FIRST_SPAN, SECOND_SPAN);
                int firstSpanSize = SpanBytesEncoder.JSON_V2.encode(FIRST_SPAN).length;

                assertThat(sender.messageMaxBytes()).isEqualTo(1024);
                assertThat(sender.messageSizeInBytes(encodedSpans)).isEqualTo(Encoding.JSON.listSizeInBytes(encodedSpans));
                assertThat(sender.messageSizeInBytes(firstSpanSize)).isEqualTo(Encoding.JSON.listSizeInBytes(firstSpanSize));

                sender.sendSpans(encodedSpans).execute();

                CapturedRequest request = collector.takeRequest();
                assertThat(request.header("Content-Encoding")).isNull();
                assertThat(SpanBytesDecoder.JSON_V2.decodeList(request.body())).containsExactly(FIRST_SPAN, SECOND_SPAN);
            } finally {
                sender.close();
            }
        }
    }

    @Test
    void senderPostsProto3SpansWhenConfigured() throws Exception {
        try (TestCollector collector = new TestCollector()) {
            OkHttpSender sender = OkHttpSender.newBuilder()
                    .endpoint(collector.endpoint())
                    .encoding(Encoding.PROTO3)
                    .compressionEnabled(false)
                    .build();
            try {
                List<byte[]> encodedSpans = List.of(
                        SpanBytesEncoder.PROTO3.encode(FIRST_SPAN),
                        SpanBytesEncoder.PROTO3.encode(SECOND_SPAN));

                sender.sendSpans(encodedSpans).execute();

                CapturedRequest request = collector.takeRequest();
                assertThat(request.header("Content-Type")).isEqualTo("application/x-protobuf");
                assertThat(request.header("Content-Encoding")).isNull();
                assertThat(SpanBytesDecoder.PROTO3.decodeList(request.body())).containsExactly(FIRST_SPAN, SECOND_SPAN);
            } finally {
                sender.close();
            }
        }
    }

    @Test
    void senderToBuilderCanReuseConfigurationWithDifferentEndpoint() throws Exception {
        try (TestCollector primaryCollector = new TestCollector();
                TestCollector secondaryCollector = new TestCollector()) {
            OkHttpSender originalSender = OkHttpSender.newBuilder()
                    .endpoint(primaryCollector.endpoint())
                    .compressionEnabled(false)
                    .messageMaxBytes(2048)
                    .build();
            OkHttpSender derivedSender = null;
            try {
                HttpUrl secondaryEndpoint = HttpUrl.parse(secondaryCollector.endpoint());
                assertThat(secondaryEndpoint).isNotNull();

                derivedSender = originalSender.toBuilder()
                        .endpoint(secondaryEndpoint)
                        .build();

                assertThat(derivedSender.encoding()).isEqualTo(originalSender.encoding());
                assertThat(derivedSender.messageMaxBytes()).isEqualTo(originalSender.messageMaxBytes());
                assertThat(derivedSender.toString()).isEqualTo("OkHttpSender{" + secondaryCollector.endpoint() + "}");

                derivedSender.sendSpans(jsonSpans(FIRST_SPAN)).execute();

                CapturedRequest request = secondaryCollector.takeRequest();
                assertThat(request.header("Content-Encoding")).isNull();
                assertThat(SpanBytesDecoder.JSON_V2.decodeList(request.body())).containsExactly(FIRST_SPAN);
            } finally {
                if (derivedSender != null) {
                    derivedSender.close();
                }
                originalSender.close();
            }
        }
    }

    @Test
    void customOkHttpClientBuilderCanDecorateOutgoingRequests() throws Exception {
        try (TestCollector collector = new TestCollector()) {
            OkHttpSender.Builder builder = OkHttpSender.newBuilder()
                    .endpoint(collector.endpoint())
                    .compressionEnabled(false);
            builder.clientBuilder().addInterceptor(chain -> {
                Request request = chain.request().newBuilder()
                        .addHeader("X-Zipkin-Sender-Test", "custom-client")
                        .build();
                return chain.proceed(request);
            });
            OkHttpSender sender = builder.build();
            try {
                sender.sendSpans(jsonSpans(FIRST_SPAN)).execute();

                CapturedRequest request = collector.takeRequest();
                assertThat(request.header("X-Zipkin-Sender-Test")).isEqualTo("custom-client");
                assertThat(SpanBytesDecoder.JSON_V2.decodeList(request.body())).containsExactly(FIRST_SPAN);
            } finally {
                sender.close();
            }
        }
    }

    @Test
    void asyncReporterFlushesThroughOkHttpSender() throws Exception {
        try (TestCollector collector = new TestCollector()) {
            OkHttpSender sender = OkHttpSender.newBuilder()
                    .endpoint(collector.endpoint())
                    .compressionEnabled(false)
                    .build();
            try (AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                    .messageTimeout(0, TimeUnit.MILLISECONDS)
                    .build()) {
                reporter.report(FIRST_SPAN);
                reporter.report(SECOND_SPAN);

                reporter.flush();

                CapturedRequest request = collector.takeRequest();
                assertThat(SpanBytesDecoder.JSON_V2.decodeList(request.body()))
                        .containsExactly(FIRST_SPAN, SECOND_SPAN);
            }
        }
    }

    @Test
    void checkPostsEmptyJsonMessageAndReflectsCollectorHealth() throws Exception {
        try (TestCollector collector = new TestCollector()) {
            OkHttpSender sender = OkHttpSender.newBuilder()
                    .endpoint(collector.endpoint())
                    .compressionEnabled(false)
                    .build();
            try {
                CheckResult healthy = sender.check();

                CapturedRequest healthyRequest = collector.takeRequest();
                assertThat(healthy.ok()).isTrue();
                assertThat(healthyRequest.header("Content-Type")).startsWith("application/json");
                assertThat(new String(healthyRequest.body(), StandardCharsets.UTF_8)).isEqualTo("[]");

                collector.responseCode(500);
                collector.responseBody("unhealthy");

                CheckResult unhealthy = sender.check();

                CapturedRequest unhealthyRequest = collector.takeRequest();
                assertThat(unhealthy.ok()).isFalse();
                assertThat(new String(unhealthyRequest.body(), StandardCharsets.UTF_8)).isEqualTo("[]");
            } finally {
                sender.close();
            }
        }
    }

    @Test
    void asynchronousCallsPropagateHttpFailuresAndSupportCancellation() throws Exception {
        try (TestCollector collector = new TestCollector()) {
            collector.responseCode(500);
            collector.responseBody("collector failed");
            OkHttpSender sender = OkHttpSender.newBuilder()
                    .endpoint(collector.endpoint())
                    .compressionEnabled(false)
                    .build();
            try {
                AwaitingCallback callback = new AwaitingCallback();

                sender.sendSpans(jsonSpans(FIRST_SPAN)).enqueue(callback);

                Throwable failure = callback.awaitFailure();
                assertThat(failure).isInstanceOf(RuntimeException.class);
                assertThat(failure).hasMessageContaining("collector failed");
                assertThat(collector.takeRequest().body()).isNotEmpty();

                Call<Void> call = sender.sendSpans(jsonSpans(SECOND_SPAN));
                call.cancel();

                assertThat(call.isCanceled()).isTrue();
            } finally {
                sender.close();
            }
        }
    }

    @Test
    void builderValidationAndClosedSenderFailuresAreReported() throws Exception {
        assertThatThrownBy(() -> OkHttpSender.create("htp://localhost:9411/api/v2/spans"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("invalid POST url: ");
        assertThatThrownBy(() -> OkHttpSender.newBuilder().endpoint((String) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("endpoint == null");
        assertThatThrownBy(() -> OkHttpSender.newBuilder().encoding(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("encoding == null");
        assertThatThrownBy(() -> OkHttpSender.newBuilder().build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("endpoint == null");

        try (TestCollector collector = new TestCollector()) {
            OkHttpSender sender = OkHttpSender.create(collector.endpoint());
            sender.close();

            assertThatThrownBy(() -> sender.sendSpans(jsonSpans(FIRST_SPAN)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    private static List<byte[]> jsonSpans(Span... spans) {
        return List.of(spans).stream()
                .map(SpanBytesEncoder.JSON_V2::encode)
                .toList();
    }

    private static Span span(String traceId, String spanId, String name, String route) {
        return Span.newBuilder()
                .traceId(traceId)
                .id(spanId)
                .name(name)
                .kind(Span.Kind.CLIENT)
                .timestamp(1_000_000L)
                .duration(150_000L)
                .putTag("http.method", "GET")
                .putTag("http.route", route)
                .build();
    }

    private static final class AwaitingCallback implements Callback<Void> {

        private final LinkedBlockingQueue<Throwable> failures = new LinkedBlockingQueue<>();

        @Override
        public void onSuccess(Void value) {
            failures.add(new AssertionError("Expected asynchronous call to fail"));
        }

        @Override
        public void onError(Throwable throwable) {
            failures.add(throwable);
        }

        private Throwable awaitFailure() throws InterruptedException {
            Throwable failure = failures.poll(5, TimeUnit.SECONDS);
            assertThat(failure).isNotNull();
            return failure;
        }
    }

    private static final class TestCollector implements AutoCloseable {

        private final HttpServer server;
        private final ExecutorService executor;
        private final LinkedBlockingQueue<CapturedRequest> requests = new LinkedBlockingQueue<>();
        private final AtomicReference<String> responseBody = new AtomicReference<>("");
        private volatile int responseCode = 202;

        private TestCollector() throws IOException {
            InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
            server = HttpServer.create(address, 0);
            executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "zipkin-okhttp3-test-collector");
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(executor);
            server.createContext("/api/v2/spans", this::handle);
            server.start();
        }

        private String endpoint() {
            return "http://" + server.getAddress().getAddress().getHostAddress()
                    + ':' + server.getAddress().getPort() + "/api/v2/spans";
        }

        private void responseCode(int responseCode) {
            this.responseCode = responseCode;
        }

        private void responseBody(String responseBody) {
            this.responseBody.set(responseBody);
        }

        private CapturedRequest takeRequest() throws InterruptedException {
            CapturedRequest request = requests.poll(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            return request;
        }

        private void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                requests.add(new CapturedRequest(
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders(),
                        exchange.getRequestBody().readAllBytes()));
                byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(responseCode, body.length);
                exchange.getResponseBody().write(body);
            }
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private record CapturedRequest(String method, String path, Headers headers, byte[] body) {

        private String header(String name) {
            return headers.getFirst(name);
        }

        private byte[] bodyAfterGzipIfNeeded() throws IOException {
            String contentEncoding = header("Content-Encoding");
            if (contentEncoding == null || !contentEncoding.toLowerCase(Locale.ROOT).contains("gzip")) {
                return body;
            }
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(body))) {
                return gzip.readAllBytes();
            }
        }
    }
}
