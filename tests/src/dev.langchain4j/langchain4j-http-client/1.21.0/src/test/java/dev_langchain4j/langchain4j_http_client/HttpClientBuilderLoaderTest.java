/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j_http_client;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.HttpResponseReceived;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class HttpClientBuilderLoaderTest {

    @Test
    @Timeout(55)
    void discoversConfiguredProviderAndExecutesRequest() throws Exception {
        ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            serverSocket.setSoTimeout(10_000);
            Future<String> requestLine = serverExecutor.submit(() -> serveSingleRequest(serverSocket));
            Duration timeout = Duration.ofSeconds(10);

            HttpClientBuilder builder = HttpClientBuilderLoader.loadHttpClientBuilder()
                    .connectTimeout(timeout)
                    .readTimeout(timeout);
            HttpClient client = builder.build();
            HttpRequest request = HttpRequest.builder()
                    .method(HttpMethod.GET)
                    .url("http://127.0.0.1:" + serverSocket.getLocalPort() + "/status")
                    .build();

            SuccessfulHttpResponse response = client.execute(request);

            assertThat(builder.connectTimeout()).isEqualTo(timeout);
            assertThat(builder.readTimeout()).isEqualTo(timeout);
            assertThat(requestLine.get(10, TimeUnit.SECONDS)).isEqualTo("GET /status HTTP/1.1");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("provider-ready");
        } finally {
            serverExecutor.shutdownNow();
            assertThat(serverExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @Timeout(55)
    void streamsServerSentEventsThroughConfiguredProvider() throws Exception {
        ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            serverSocket.setSoTimeout(10_000);
            Future<String> requestLine = serverExecutor.submit(() -> serveStreamingRequest(serverSocket));
            CountDownLatch completed = new CountDownLatch(1);
            List<HttpStreamingEvent> events = new CopyOnWriteArrayList<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();

            HttpClient client = HttpClientBuilderLoader.loadHttpClientBuilder()
                    .readTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest request = HttpRequest.builder()
                    .method(HttpMethod.GET)
                    .url("http://127.0.0.1:" + serverSocket.getLocalPort() + "/events")
                    .build();

            client.stream(request).subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(HttpStreamingEvent event) {
                    events.add(event);
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

            assertThat(completed.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(failure.get()).isNull();
            assertThat(requestLine.get(10, TimeUnit.SECONDS)).isEqualTo("GET /events HTTP/1.1");
            assertThat(events).hasSize(3);
            assertThat(events.get(0)).isInstanceOf(HttpResponseReceived.class);
            assertThat(((HttpResponseReceived) events.get(0)).response().statusCode()).isEqualTo(200);
            assertThat(events.get(1)).isEqualTo(new ServerSentEvent("update", "first"));
            assertThat(events.get(2)).isEqualTo(new ServerSentEvent(null, "second"));
        } finally {
            serverExecutor.shutdownNow();
            assertThat(serverExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @Timeout(55)
    void executesRequestsAsynchronouslyThroughConfiguredProvider() throws Exception {
        ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            serverSocket.setSoTimeout(10_000);
            Future<String> requestLine = serverExecutor.submit(() -> serveSingleRequest(serverSocket));
            Duration timeout = Duration.ofSeconds(10);

            HttpClient client = HttpClientBuilderLoader.loadHttpClientBuilder()
                    .connectTimeout(timeout)
                    .readTimeout(timeout)
                    .build();
            HttpRequest request = HttpRequest.builder()
                    .method(HttpMethod.GET)
                    .url("http://127.0.0.1:" + serverSocket.getLocalPort() + "/async-status")
                    .build();

            CompletableFuture<SuccessfulHttpResponse> responseFuture = client.executeAsync(request);
            SuccessfulHttpResponse response = responseFuture.get(10, TimeUnit.SECONDS);

            assertThat(requestLine.get(10, TimeUnit.SECONDS)).isEqualTo("GET /async-status HTTP/1.1");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("provider-ready");
        } finally {
            serverExecutor.shutdownNow();
            assertThat(serverExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static String serveStreamingRequest(ServerSocket serverSocket) throws Exception {
        try (Socket socket = serverSocket.accept();
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(socket.getInputStream(), US_ASCII))) {
            socket.setSoTimeout(10_000);
            String requestLine = reader.readLine();
            String header;
            do {
                header = reader.readLine();
            } while (header != null && !header.isEmpty());

            byte[] body = "event: update\r\ndata: first\r\n\r\ndata: second\r\n\r\n".getBytes(US_ASCII);
            String headers = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: text/event-stream\r\n"
                    + "Content-Length: "
                    + body.length
                    + "\r\n"
                    + "Connection: close\r\n\r\n";
            socket.getOutputStream().write(headers.getBytes(US_ASCII));
            socket.getOutputStream().write(body);
            socket.getOutputStream().flush();
            return requestLine;
        }
    }

    private static String serveSingleRequest(ServerSocket serverSocket) throws Exception {
        try (Socket socket = serverSocket.accept();
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(socket.getInputStream(), US_ASCII))) {
            socket.setSoTimeout(10_000);
            String requestLine = reader.readLine();
            String header;
            do {
                header = reader.readLine();
            } while (header != null && !header.isEmpty());

            byte[] body = "provider-ready".getBytes(US_ASCII);
            String headers = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: text/plain; charset=US-ASCII\r\n"
                    + "Content-Length: "
                    + body.length
                    + "\r\n"
                    + "Connection: close\r\n\r\n";
            socket.getOutputStream().write(headers.getBytes(US_ASCII));
            socket.getOutputStream().write(body);
            socket.getOutputStream().flush();
            return requestLine;
        }
    }
}
