/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_zipkin_reporter2.zipkin_reporter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import okhttp3.Request;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.reporter.BytesMessageSender;
import zipkin2.reporter.Encoding;
import zipkin2.reporter.SpanBytesEncoder;
import zipkin2.reporter.okhttp3.OkHttpSender;
import zipkin2.reporter.urlconnection.URLConnectionSender;

/**
 * Tuning the bundled HTTP senders, `URLConnectionSender` and `OkHttpSender`, as an application
 * configures them: gzip compression switched on or off, OkHttp client customization through
 * `clientBuilder()` carried across `toBuilder()`, read timeouts against a stalled collector,
 * collector error statuses, and sizing of the messages they post with `send`.
 *
 * An inventory service ships its spans straight through a sender, without a reporter, from a
 * batch job that already holds encoded spans. A JDK `HttpServer` plays the Zipkin collector: it
 * records every POST with its headers and raw body, and can be told to reject or stall requests
 * so each test observes what an operator would see at the collector or in the job's logs.
 */
public class HttpSenderTuningTest {

    private static final String TRACE_ID = "7c1e9f04a2b35d68e0f4a19b2c6d8e31";
    private static final String TENANT_HEADER = "X-Tenant";

    private Collector collector;

    @BeforeEach
    void startCollector() throws IOException {
        collector = Collector.start();
    }

    @AfterEach
    void stopCollector() {
        collector.stop();
    }

    @Test
    void compressionIsOnByDefaultAndCanBeSwitchedOffPerSender() throws Exception {
        List<Span> spans = List.of(
                span("0000000000000001", "reserve-stock"),
                span("0000000000000002", "release-stock"));
        List<byte[]> encoded = encode(SpanBytesEncoder.JSON_V2, spans);

        List<BytesMessageSender> senders = List.of(
                URLConnectionSender.create(collector.url()),
                URLConnectionSender.newBuilder().endpoint(collector.url()).compressionEnabled(false).build(),
                OkHttpSender.create(collector.url()),
                OkHttpSender.newBuilder().endpoint(collector.url()).compressionEnabled(false).build());
        try {
            for (BytesMessageSender sender : senders) {
                sender.send(encoded);
            }

            Post urlGzip = collector.next();
            Post urlPlain = collector.next();
            Post okGzip = collector.next();
            Post okPlain = collector.next();

            for (Post gzipped : List.of(urlGzip, okGzip)) {
                assertEquals("gzip", gzipped.contentEncoding());
                assertEquals("application/json", gzipped.contentType());
                assertEquals(spans, SpanBytesDecoder.JSON_V2.decodeList(gunzip(gzipped.rawBody())));
            }
            // Without compression the collector receives exactly the framed JSON list, and its
            // length is what the sender reports as the message size.
            byte[] expectedBody = Encoding.JSON.encode(encoded);
            for (Post plain : List.of(urlPlain, okPlain)) {
                assertNull(plain.contentEncoding());
                assertEquals("application/json", plain.contentType());
                assertArrayEquals(expectedBody, plain.rawBody());
                assertEquals(String.valueOf(expectedBody.length), plain.contentLength());
            }
            for (BytesMessageSender sender : senders) {
                assertEquals(Encoding.JSON, sender.encoding());
                assertEquals(expectedBody.length, sender.messageSizeInBytes(encoded));
                assertEquals(Encoding.JSON.listSizeInBytes(encoded), sender.messageSizeInBytes(encoded));
            }
        } finally {
            for (BytesMessageSender sender : senders) {
                sender.close();
            }
        }
    }

    @Test
    void okHttpClientCustomizationIsKeptWhenTheSenderIsRebuilt() throws Exception {
        OkHttpSender.Builder builder = OkHttpSender.newBuilder()
                .endpoint(collector.url())
                .compressionEnabled(false)
                .messageMaxBytes(64 * 1024);
        builder.clientBuilder().addInterceptor(chain -> {
            Request tagged = chain.request().newBuilder().header(TENANT_HEADER, "warehouse-eu").build();
            return chain.proceed(tagged);
        });
        OkHttpSender jsonSender = builder.build();
        // A second deployment switches the wire format but keeps the configured HTTP client.
        OkHttpSender protoSender = jsonSender.toBuilder().encoding(Encoding.PROTO3).build();
        try {
            assertEquals(64 * 1024, jsonSender.messageMaxBytes());
            assertEquals(64 * 1024, protoSender.messageMaxBytes());
            assertEquals(Encoding.PROTO3, protoSender.encoding());

            Span count = span("0000000000000003", "cycle-count");
            jsonSender.send(encode(SpanBytesEncoder.JSON_V2, List.of(count)));
            Span restock = span("0000000000000004", "restock-shelf");
            List<byte[]> protoEncoded = encode(SpanBytesEncoder.PROTO3, List.of(restock));
            protoSender.send(protoEncoded);

            Post json = collector.next();
            assertEquals("warehouse-eu", json.tenant());
            assertEquals("application/json", json.contentType());
            assertEquals(List.of(count), SpanBytesDecoder.JSON_V2.decodeList(json.rawBody()));

            Post proto = collector.next();
            assertEquals("warehouse-eu", proto.tenant());
            assertEquals("application/x-protobuf", proto.contentType());
            assertArrayEquals(Encoding.PROTO3.encode(protoEncoded), proto.rawBody());
            assertEquals(List.of(restock), SpanBytesDecoder.PROTO3.decodeList(proto.rawBody()));
        } finally {
            protoSender.close();
            jsonSender.close();
        }
    }

    @Test
    void readTimeoutAbandonsAStalledCollector() throws Exception {
        List<byte[]> encoded = encode(SpanBytesEncoder.JSON_V2, List.of(span("0000000000000005", "pick-order")));
        URLConnectionSender urlSender = URLConnectionSender.newBuilder()
                .endpoint(collector.url())
                .readTimeout(300)
                .build();
        OkHttpSender okSender = OkHttpSender.newBuilder()
                .endpoint(collector.url())
                .readTimeout(300)
                .build();
        collector.stall();
        try {
            assertThrows(SocketTimeoutException.class, () -> urlSender.send(encoded));
            assertThrows(SocketTimeoutException.class, () -> okSender.send(encoded));
            // Both requests reached the collector before the senders gave up on the response.
            assertNotNull(collector.next());
            assertNotNull(collector.next());
        } finally {
            collector.release();
            urlSender.close();
            okSender.close();
        }
    }

    @Test
    void collectorErrorStatusFailsTheSend() throws Exception {
        List<byte[]> encoded = encode(SpanBytesEncoder.JSON_V2, List.of(span("0000000000000006", "ship-order")));
        URLConnectionSender urlSender = URLConnectionSender.newBuilder()
                .endpoint(collector.url())
                .compressionEnabled(false)
                .build();
        OkHttpSender okSender = OkHttpSender.newBuilder()
                .endpoint(collector.url())
                .compressionEnabled(false)
                .build();
        collector.reject(400, "Malformed reading span");
        try {
            // URLConnection reports the status as an IOException; OkHttp rethrows the collector's
            // error body unchecked, so the job's log shows why the collector refused the spans.
            IOException urlFailure = assertThrows(IOException.class, () -> urlSender.send(encoded));
            assertTrue(urlFailure.getMessage().contains("400"), urlFailure.getMessage());
            RuntimeException okFailure = assertThrows(RuntimeException.class, () -> okSender.send(encoded));
            assertTrue(okFailure.getMessage().endsWith("failed: Malformed reading span"), okFailure.getMessage());
            assertNotNull(collector.next());
            assertNotNull(collector.next());

            // Once the collector recovers, the same senders deliver again.
            collector.accept();
            urlSender.send(encoded);
            Post delivered = collector.next();
            assertEquals("ship-order", SpanBytesDecoder.JSON_V2.decodeList(delivered.rawBody()).get(0).name());
            assertTrue(urlSender.toString().contains(collector.url()), urlSender.toString());
            assertTrue(okSender.toString().contains(collector.url()), okSender.toString());
        } finally {
            urlSender.close();
            okSender.close();
        }
    }

    private static List<byte[]> encode(SpanBytesEncoder encoder, List<Span> spans) {
        return spans.stream().map(encoder::encode).collect(Collectors.toList());
    }

    private static byte[] gunzip(byte[] body) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(body))) {
            return in.readAllBytes();
        }
    }

    private static Span span(String id, String name) {
        return Span.newBuilder()
                .traceId(TRACE_ID)
                .id(id)
                .name(name)
                .kind(Span.Kind.SERVER)
                .localEndpoint(Endpoint.newBuilder().serviceName("inventory").ip("10.1.2.3").port(8080).build())
                .timestamp(1_700_000_000_000_000L)
                .duration(1_250L)
                .putTag("warehouse", "eu-west")
                .build();
    }

    /// A request as the collector received it, body untouched.
    record Post(String contentType, String contentEncoding, String contentLength, String tenant,
                byte[] rawBody) {
    }

    /// A JDK `HttpServer` standing in for the Zipkin collector's `/api/v2/spans` endpoint.
    static final class Collector {

        private final HttpServer server;
        private final ExecutorService executor = Executors.newFixedThreadPool(4);
        private final BlockingQueue<Post> posts = new LinkedBlockingQueue<>();
        private volatile int status = 202;
        private volatile String errorBody = "";
        private volatile CountDownLatch stall = new CountDownLatch(0);

        private Collector(HttpServer server) {
            this.server = server;
            server.createContext("/api/v2/spans", this::handle);
            server.setExecutor(executor);
            server.start();
        }

        static Collector start() throws IOException {
            InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
            return new Collector(HttpServer.create(address, 0));
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v2/spans";
        }

        Post next() throws InterruptedException {
            Post post = posts.poll(10, TimeUnit.SECONDS);
            assertNotNull(post, "the collector received no request");
            return post;
        }

        void reject(int rejectStatus, String body) {
            errorBody = body;
            status = rejectStatus;
        }

        void accept() {
            status = 202;
        }

        void stall() {
            stall = new CountDownLatch(1);
        }

        void release() {
            stall.countDown();
        }

        void stop() {
            release();
            server.stop(0);
            executor.shutdownNow();
        }

        private void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                byte[] body;
                try (InputStream in = exchange.getRequestBody()) {
                    body = in.readAllBytes();
                }
                posts.add(new Post(
                        exchange.getRequestHeaders().getFirst("Content-Type"),
                        exchange.getRequestHeaders().getFirst("Content-Encoding"),
                        exchange.getRequestHeaders().getFirst("Content-Length"),
                        exchange.getRequestHeaders().getFirst(TENANT_HEADER),
                        body));
                try {
                    stall.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                int code = status;
                if (code == 202) {
                    exchange.sendResponseHeaders(code, -1);
                    return;
                }
                byte[] message = errorBody.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(code, message.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(message);
                }
            }
        }
    }
}
