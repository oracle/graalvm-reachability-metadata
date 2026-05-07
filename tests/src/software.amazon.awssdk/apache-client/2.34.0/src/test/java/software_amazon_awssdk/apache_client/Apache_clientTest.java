/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.apache_client;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.http.conn.DnsResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.ExecutableHttpRequest;
import software.amazon.awssdk.http.Header;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpService;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ApacheSdkHttpService;
import software.amazon.awssdk.http.apache.ProxyConfiguration;

@Timeout(60)
public class Apache_clientTest {
    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(2);

    @Test
    void apacheClientExecutesGetRequestAndExposesResponseMetadata() throws Exception {
        try (TestHttpServer server = TestHttpServer.create(exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestURI().getPath()).isEqualTo("/hello");
            assertThat(exchange.getRequestURI().getRawQuery()).contains("greeting=hello", "symbol=");
            assertThat(exchange.getRequestHeaders().get("X-Test")).containsExactly("one", "two");
            assertThat(exchange.getRequestHeaders().getFirst("Accept")).isEqualTo("text/plain");

            exchange.getResponseHeaders().put("X-Reply", List.of("alpha", "beta"));
            writeResponse(exchange, 201, "hello from apache");
        }); SdkHttpClient client = newClient()) {
            SdkHttpFullRequest request = SdkHttpFullRequest.builder()
                    .method(SdkHttpMethod.GET)
                    .uri(server.uri("/hello"))
                    .appendRawQueryParameter("greeting", "hello world")
                    .appendRawQueryParameter("symbol", "a+b&c=d")
                    .appendHeader("X-Test", "one")
                    .appendHeader("X-Test", "two")
                    .putHeader(Header.ACCEPT, "text/plain")
                    .build();

            HttpExecuteResponse response = client.prepareRequest(HttpExecuteRequest.builder()
                    .request(request)
                    .build()).call();

            assertThat(response.httpResponse().statusCode()).isEqualTo(201);
            assertThat(response.httpResponse().isSuccessful()).isTrue();
            assertThat(response.httpResponse().firstMatchingHeader("X-Reply")).contains("alpha");
            assertThat(readResponse(response)).isEqualTo("hello from apache");
        }
    }

    @Test
    void apacheClientSendsRepeatablePostBodies() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        try (TestHttpServer server = TestHttpServer.create(exchange -> {
            int requestNumber = requests.incrementAndGet();
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(exchange.getRequestURI().getPath()).isEqualTo("/submit");
            assertThat(exchange.getRequestHeaders().getFirst(Header.CONTENT_TYPE))
                    .isEqualTo("text/plain; charset=utf-8");
            assertThat(new String(exchange.getRequestBody().readAllBytes(), UTF_8))
                    .isEqualTo("payload-" + requestNumber);
            writeResponse(exchange, 200, "accepted-" + requestNumber);
        }); SdkHttpClient client = newClient()) {
            assertThat(postPayload(client, server.uri("/submit"), "payload-1")).isEqualTo("accepted-1");
            assertThat(postPayload(client, server.uri("/submit"), "payload-2")).isEqualTo("accepted-2");
            assertThat(requests).hasValue(2);
        }
    }

    @Test
    void apacheClientUsesConfiguredHttpProxyAndProxyCredentials() throws Exception {
        String expectedAuthorization = "Basic "
                + Base64.getEncoder().encodeToString("proxyUser:secret".getBytes(UTF_8));
        AtomicInteger proxyRequests = new AtomicInteger();
        try (TestHttpServer proxy = TestHttpServer.create(exchange -> {
            proxyRequests.incrementAndGet();
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestURI().toString()).contains("/proxied");
            assertThat(exchange.getRequestURI().getRawQuery()).contains("via=proxy");
            assertThat(exchange.getRequestHeaders().getFirst("Host")).contains("example.com");
            if (exchange.getRequestHeaders().getFirst("Proxy-Authorization") == null) {
                exchange.getResponseHeaders().add("Proxy-Authenticate", "Basic realm=\"apache-client-test\"");
                writeResponse(exchange, 407, "proxy authentication required");
                return;
            }
            assertThat(exchange.getRequestHeaders().getFirst("Proxy-Authorization")).isEqualTo(expectedAuthorization);
            writeResponse(exchange, 200, "from-proxy");
        }); SdkHttpClient client = ApacheHttpClient.builder()
                .connectionTimeout(SHORT_TIMEOUT)
                .socketTimeout(SHORT_TIMEOUT)
                .connectionAcquisitionTimeout(SHORT_TIMEOUT)
                .useIdleConnectionReaper(false)
                .proxyConfiguration(ProxyConfiguration.builder()
                        .endpoint(proxy.uri(""))
                        .username("proxyUser")
                        .password("secret")
                        .preemptiveBasicAuthenticationEnabled(true)
                        .nonProxyHosts(Set.of("localhost"))
                        .useSystemPropertyValues(false)
                        .useEnvironmentVariableValues(false)
                        .build())
                .build()) {
            SdkHttpFullRequest request = SdkHttpFullRequest.builder()
                    .method(SdkHttpMethod.GET)
                    .uri(URI.create("http://example.com/proxied?via=proxy"))
                    .build();

            assertThat(readResponse(client.prepareRequest(HttpExecuteRequest.builder()
                    .request(request)
                    .build()).call())).isEqualTo("from-proxy");
            assertThat(proxyRequests.get()).isBetween(1, 2);
        }
    }

    @Test
    void apacheClientBypassesProxyForConfiguredNonProxyHosts() throws Exception {
        AtomicInteger originRequests = new AtomicInteger();
        AtomicInteger proxyRequests = new AtomicInteger();
        try (TestHttpServer origin = TestHttpServer.create(exchange -> {
            originRequests.incrementAndGet();
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestURI().getPath()).isEqualTo("/direct");
            writeResponse(exchange, 200, "from-origin");
        }); TestHttpServer proxy = TestHttpServer.create(exchange -> {
            proxyRequests.incrementAndGet();
            writeResponse(exchange, 502, "proxy should not be used");
        }); SdkHttpClient client = ApacheHttpClient.builder()
                .connectionTimeout(SHORT_TIMEOUT)
                .socketTimeout(SHORT_TIMEOUT)
                .connectionAcquisitionTimeout(SHORT_TIMEOUT)
                .useIdleConnectionReaper(false)
                .proxyConfiguration(ProxyConfiguration.builder()
                        .endpoint(proxy.uri(""))
                        .nonProxyHosts(Set.of(origin.uri("").getHost()))
                        .useSystemPropertyValues(false)
                        .useEnvironmentVariableValues(false)
                        .build())
                .build()) {
            SdkHttpFullRequest request = SdkHttpFullRequest.builder()
                    .method(SdkHttpMethod.GET)
                    .uri(origin.uri("/direct"))
                    .build();

            HttpExecuteResponse response = client.prepareRequest(HttpExecuteRequest.builder()
                    .request(request)
                    .build()).call();

            assertThat(response.httpResponse().statusCode()).isEqualTo(200);
            assertThat(readResponse(response)).isEqualTo("from-origin");
            assertThat(originRequests).hasValue(1);
            assertThat(proxyRequests).hasValue(0);
        }
    }

    @Test
    void apacheClientUsesCustomDnsResolver() throws Exception {
        AtomicReference<String> resolvedHost = new AtomicReference<>();
        DnsResolver resolver = host -> {
            resolvedHost.set(host);
            if ("aws.test".equals(host)) {
                return new InetAddress[] { InetAddress.getLoopbackAddress() };
            }
            return InetAddress.getAllByName(host);
        };

        try (TestHttpServer server = TestHttpServer.create(exchange -> {
            assertThat(exchange.getRequestURI().getPath()).isEqualTo("/resolved");
            assertThat(exchange.getRequestHeaders().getFirst("Host")).startsWith("aws.test:");
            writeResponse(exchange, 200, "resolved");
        }); SdkHttpClient client = ApacheHttpClient.builder()
                .connectionTimeout(SHORT_TIMEOUT)
                .socketTimeout(SHORT_TIMEOUT)
                .connectionAcquisitionTimeout(SHORT_TIMEOUT)
                .useIdleConnectionReaper(false)
                .dnsResolver(resolver)
                .build()) {
            SdkHttpFullRequest request = SdkHttpFullRequest.builder()
                    .method(SdkHttpMethod.GET)
                    .uri(URI.create("http://aws.test:" + server.port() + "/resolved"))
                    .build();

            assertThat(readResponse(client.prepareRequest(HttpExecuteRequest.builder()
                    .request(request)
                    .build()).call())).isEqualTo("resolved");
            assertThat(resolvedHost).hasValue("aws.test");
        }
    }

    @Test
    void apacheHttpServiceIsDiscoverableAndCreatesApacheClientBuilder() {
        Optional<SdkHttpService> service = ServiceLoader.load(SdkHttpService.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(ApacheSdkHttpService.class::isInstance)
                .findFirst();

        assertThat(service).isPresent();
        assertThat(service.orElseThrow().createHttpClientBuilder()).isInstanceOf(ApacheHttpClient.Builder.class);
        try (SdkHttpClient client = ((ApacheHttpClient.Builder) service.orElseThrow().createHttpClientBuilder())
                .connectionTimeout(SHORT_TIMEOUT)
                .socketTimeout(SHORT_TIMEOUT)
                .connectionAcquisitionTimeout(SHORT_TIMEOUT)
                .useIdleConnectionReaper(false)
                .build()) {
            assertThat(client.clientName()).isEqualTo(ApacheHttpClient.CLIENT_NAME);
        }
    }

    private static SdkHttpClient newClient() {
        return ApacheHttpClient.builder()
                .connectionTimeout(SHORT_TIMEOUT)
                .socketTimeout(SHORT_TIMEOUT)
                .connectionAcquisitionTimeout(SHORT_TIMEOUT)
                .maxConnections(4)
                .useIdleConnectionReaper(false)
                .build();
    }

    private static String postPayload(SdkHttpClient client, URI uri, String body) throws IOException {
        ContentStreamProvider content = ContentStreamProvider.fromUtf8String(body);
        SdkHttpFullRequest request = SdkHttpFullRequest.builder()
                .method(SdkHttpMethod.POST)
                .uri(uri)
                .putHeader(Header.CONTENT_TYPE, "text/plain; charset=utf-8")
                .putHeader(Header.CONTENT_LENGTH, Integer.toString(body.getBytes(UTF_8).length))
                .build();
        ExecutableHttpRequest executable = client.prepareRequest(HttpExecuteRequest.builder()
                .request(request)
                .contentStreamProvider(content)
                .build());
        return readResponse(executable.call());
    }

    private static String readResponse(HttpExecuteResponse response) throws IOException {
        try (AbortableInputStream stream = response.responseBody().orElseThrow()) {
            return new String(stream.readAllBytes(), UTF_8);
        }
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
            ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "apache-client-test-server");
                thread.setDaemon(true);
                return thread;
            });
            server.createContext("/", exchange -> {
                try (exchange) {
                    handler.handle(exchange);
                }
            });
            server.setExecutor(executor);
            server.start();
            return new TestHttpServer(server, executor);
        }

        URI uri(String path) {
            return URI.create("http://" + server.getAddress().getHostString() + ":" + port() + path);
        }

        int port() {
            return server.getAddress().getPort();
        }

        @Override
        public void close() throws InterruptedException {
            server.stop(0);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
