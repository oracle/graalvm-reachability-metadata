/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.netty_nio_client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpResponseHandler;
import software.amazon.awssdk.http.async.SdkHttpContentPublisher;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.ProxyConfiguration;
import software.amazon.awssdk.http.nio.netty.SdkEventLoopGroup;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DnsResolverLoaderTest {
    @Test
    @Timeout(120)
    void sendsRequestWithNonBlockingDnsResolver() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/message", exchange -> {
            try (exchange) {
                byte[] body = (exchange.getRequestMethod() + " " + exchange.getRequestURI())
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("X-Resolver-Test", "loopback");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        try (SdkAsyncHttpClient client = NettyNioAsyncHttpClient.builder()
                .useNonBlockingDnsResolver(true)
                .eventLoopGroupBuilder(SdkEventLoopGroup.builder().numberOfThreads(1))
                .connectionTimeout(Duration.ofSeconds(30))
                .connectionAcquisitionTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(30))
                .writeTimeout(Duration.ofSeconds(30))
                .proxyConfiguration(ProxyConfiguration.builder()
                        .useSystemPropertyValues(false)
                        .useEnvironmentVariableValues(false)
                        .build())
                .build()) {
            CollectingResponseHandler handler = new CollectingResponseHandler();
            SdkHttpRequest request = SdkHttpRequest.builder()
                    .uri(URI.create("http://localhost:" + server.getAddress().getPort() + "/message?mode=async"))
                    .method(SdkHttpMethod.GET)
                    .build();

            client.execute(AsyncExecuteRequest.builder()
                    .request(request)
                    .requestContentPublisher(new EmptyRequestBody())
                    .responseHandler(handler)
                    .build()).get(30, TimeUnit.SECONDS);

            assertEquals("GET /message?mode=async", handler.body.get(30, TimeUnit.SECONDS));
            assertEquals(200, handler.response.statusCode());
            assertEquals(Optional.of("loopback"), handler.response.firstMatchingHeader("X-Resolver-Test"));
        } finally {
            server.stop(0);
        }
    }

    private static final class EmptyRequestBody implements SdkHttpContentPublisher {
        @Override
        public Optional<Long> contentLength() {
            return Optional.of(0L);
        }

        @Override
        public void subscribe(Subscriber<? super ByteBuffer> subscriber) {
            subscriber.onSubscribe(new Subscription() {
                private boolean completed;

                @Override
                public void request(long count) {
                    if (!completed) {
                        completed = true;
                        subscriber.onComplete();
                    }
                }

                @Override
                public void cancel() {
                    completed = true;
                }
            });
        }
    }

    private static final class CollectingResponseHandler implements SdkAsyncHttpResponseHandler {
        private final CompletableFuture<String> body = new CompletableFuture<>();
        private SdkHttpResponse response;

        @Override
        public void onHeaders(SdkHttpResponse headers) {
            response = headers;
        }

        @Override
        public void onStream(Publisher<ByteBuffer> stream) {
            stream.subscribe(new Subscriber<ByteBuffer>() {
                private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

                @Override
                public void onSubscribe(Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(ByteBuffer buffer) {
                    byte[] chunk = new byte[buffer.remaining()];
                    buffer.get(chunk);
                    bytes.writeBytes(chunk);
                }

                @Override
                public void onError(Throwable error) {
                    body.completeExceptionally(error);
                }

                @Override
                public void onComplete() {
                    body.complete(bytes.toString(StandardCharsets.UTF_8));
                }
            });
        }

        @Override
        public void onError(Throwable error) {
            body.completeExceptionally(error);
        }
    }
}
