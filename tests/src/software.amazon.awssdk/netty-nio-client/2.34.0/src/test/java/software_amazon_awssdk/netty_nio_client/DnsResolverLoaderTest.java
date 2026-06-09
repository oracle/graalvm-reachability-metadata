/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.netty_nio_client;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpResponseHandler;
import software.amazon.awssdk.http.async.SdkHttpContentPublisher;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;

@Timeout(60)
public class DnsResolverLoaderTest {
    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(2);

    @Test
    void nettyClientExecutesRequestWithNonBlockingDnsResolver() throws Exception {
        try (TestHttpServer server = TestHttpServer.create(exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestURI().getPath()).isEqualTo("/dns");
            writeResponse(exchange, 200, "resolved");
        }); SdkAsyncHttpClient client = NettyNioAsyncHttpClient.builder()
                .connectionTimeout(SHORT_TIMEOUT)
                .connectionAcquisitionTimeout(SHORT_TIMEOUT)
                .readTimeout(SHORT_TIMEOUT)
                .writeTimeout(SHORT_TIMEOUT)
                .useNonBlockingDnsResolver(true)
                .build()) {
            RecordingResponseHandler handler = new RecordingResponseHandler();
            SdkHttpFullRequest request = SdkHttpFullRequest.builder()
                    .method(SdkHttpMethod.GET)
                    .uri(server.uri("/dns"))
                    .build();

            CompletableFuture<Void> execution = client.execute(AsyncExecuteRequest.builder()
                    .request(request)
                    .requestContentPublisher(new EmptyContentPublisher())
                    .responseHandler(handler)
                    .build());

            execution.get(10, TimeUnit.SECONDS);
            ResponseResult result = handler.result().get(10, TimeUnit.SECONDS);

            assertThat(result.response().statusCode()).isEqualTo(200);
            assertThat(result.body()).isEqualTo("resolved");
        }
    }

    private static void writeResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream response = exchange.getResponseBody()) {
            response.write(bytes);
        }
    }

    private static final class EmptyContentPublisher implements SdkHttpContentPublisher {
        @Override
        public Optional<Long> contentLength() {
            return Optional.of(0L);
        }

        @Override
        public void subscribe(Subscriber<? super ByteBuffer> subscriber) {
            subscriber.onSubscribe(new Subscription() {
                private final AtomicBoolean completed = new AtomicBoolean();

                @Override
                public void request(long n) {
                    if (n > 0 && completed.compareAndSet(false, true)) {
                        subscriber.onComplete();
                    }
                }

                @Override
                public void cancel() {
                    completed.set(true);
                }
            });
        }
    }

    private static final class RecordingResponseHandler implements SdkAsyncHttpResponseHandler {
        private final AtomicReference<SdkHttpResponse> response = new AtomicReference<>();
        private final CompletableFuture<ResponseResult> result = new CompletableFuture<>();

        @Override
        public void onHeaders(SdkHttpResponse response) {
            this.response.set(response);
        }

        @Override
        public void onStream(Publisher<ByteBuffer> publisher) {
            publisher.subscribe(new Subscriber<ByteBuffer>() {
                private final ByteArrayOutputStream body = new ByteArrayOutputStream();

                @Override
                public void onSubscribe(Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(ByteBuffer byteBuffer) {
                    ByteBuffer copy = byteBuffer.asReadOnlyBuffer();
                    byte[] bytes = new byte[copy.remaining()];
                    copy.get(bytes);
                    body.writeBytes(bytes);
                }

                @Override
                public void onError(Throwable throwable) {
                    result.completeExceptionally(throwable);
                }

                @Override
                public void onComplete() {
                    result.complete(new ResponseResult(response.get(), body.toString(UTF_8)));
                }
            });
        }

        @Override
        public void onError(Throwable error) {
            result.completeExceptionally(error);
        }

        CompletableFuture<ResponseResult> result() {
            return result;
        }
    }

    private record ResponseResult(SdkHttpResponse response, String body) {
    }

    private static final class TestHttpServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;

        private TestHttpServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static TestHttpServer create(HttpHandler handler) throws Exception {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            server.createContext("/", handler);
            server.setExecutor(executor);
            server.start();
            return new TestHttpServer(server, executor);
        }

        URI uri(String path) {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
        }

        @Override
        public void close() throws Exception {
            server.stop(0);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
