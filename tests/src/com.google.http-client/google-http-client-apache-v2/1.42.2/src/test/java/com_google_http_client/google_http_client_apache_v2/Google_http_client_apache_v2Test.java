/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_http_client.google_http_client_apache_v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.HttpClient;
import org.junit.jupiter.api.Test;

public class Google_http_client_apache_v2Test {
    private static final int TIMEOUT_MILLIS = 2_000;

    @Test
    void executesGetRequestAndExposesResponseMetadata() throws Exception {
        AtomicReference<RecordedRequest> recordedRequest = new AtomicReference<>();
        try (TestHttpServer server = TestHttpServer.start(exchange -> {
            recordedRequest.set(RecordedRequest.from(exchange));
            byte[] responseBody = "hello from apache transport".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.getResponseHeaders().set("X-Transport", "apache-v2");
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
        })) {
            ApacheHttpTransport transport = new ApacheHttpTransport();
            try {
                HttpRequestFactory requestFactory = transport.createRequestFactory();
                HttpRequest request = requestFactory.buildGetRequest(
                        new GenericUrl(server.url("/resource", "name=Native+Image")));
                request.setConnectTimeout(TIMEOUT_MILLIS);
                request.setReadTimeout(TIMEOUT_MILLIS);
                request.getHeaders().set("X-Client-Header", "present");

                HttpResponse response = request.execute();
                try {
                    assertThat(response.getStatusCode()).isEqualTo(200);
                    assertThat(response.getContentType()).isEqualTo("text/plain; charset=UTF-8");
                    assertThat(response.getHeaders().getFirstHeaderStringValue("X-Transport"))
                            .isEqualTo("apache-v2");
                    assertThat(response.parseAsString()).isEqualTo("hello from apache transport");
                } finally {
                    response.disconnect();
                }
            } finally {
                transport.shutdown();
            }
        }

        RecordedRequest request = recordedRequest.get();
        assertThat(request.method()).isEqualTo(HttpMethods.GET);
        assertThat(request.path()).isEqualTo("/resource");
        assertThat(request.query()).isEqualTo("name=Native%20Image");
        assertThat(request.firstHeader("X-Client-Header")).isEqualTo("present");
    }

    @Test
    void sendsPostRequestWithFixedLengthContent() throws Exception {
        AtomicReference<RecordedRequest> recordedRequest = new AtomicReference<>();
        try (TestHttpServer server = TestHttpServer.start(exchange -> {
            recordedRequest.set(RecordedRequest.from(exchange));
            byte[] responseBody = "created".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.getResponseHeaders().set("Location", "/items/1");
            exchange.sendResponseHeaders(201, responseBody.length);
            exchange.getResponseBody().write(responseBody);
        })) {
            ApacheHttpTransport transport = new ApacheHttpTransport();
            try {
                String json = "{\"message\":\"native\"}";
                HttpRequest request = transport.createRequestFactory()
                        .buildPostRequest(
                                new GenericUrl(server.url("/items", null)),
                                ByteArrayContent.fromString(
                                        "application/json; charset=UTF-8", json));
                request.setConnectTimeout(TIMEOUT_MILLIS);
                request.setReadTimeout(TIMEOUT_MILLIS);

                HttpResponse response = request.execute();
                try {
                    assertThat(response.getStatusCode()).isEqualTo(201);
                    assertThat(response.getHeaders().getFirstHeaderStringValue("Location"))
                            .isEqualTo("/items/1");
                    assertThat(response.parseAsString()).isEqualTo("created");
                } finally {
                    response.disconnect();
                }
            } finally {
                transport.shutdown();
            }
        }

        RecordedRequest request = recordedRequest.get();
        assertThat(request.method()).isEqualTo(HttpMethods.POST);
        assertThat(request.path()).isEqualTo("/items");
        assertThat(request.firstHeader("Content-Type"))
                .isEqualTo("application/json; charset=UTF-8");
        assertThat(request.bodyAsString()).isEqualTo("{\"message\":\"native\"}");
        assertThat(request.firstHeader("Content-Length"))
                .isEqualTo(Integer.toString(request.body().length));
    }

    @Test
    void executesExtensionMethodWithStreamingContent() throws Exception {
        AtomicReference<RecordedRequest> recordedRequest = new AtomicReference<>();
        try (TestHttpServer server = TestHttpServer.start(exchange -> {
            recordedRequest.set(RecordedRequest.from(exchange));
            byte[] responseBody = "reported".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
        })) {
            ApacheHttpTransport transport = new ApacheHttpTransport();
            try {
                byte[] reportBody = "streamed report body".getBytes(StandardCharsets.UTF_8);
                InputStreamContent content = new InputStreamContent(
                        "text/plain", new ByteArrayInputStream(reportBody));
                HttpRequest request = transport.createRequestFactory()
                        .buildRequest(
                                "REPORT",
                                new GenericUrl(server.url("/reports/current", null)),
                                content);
                request.setConnectTimeout(TIMEOUT_MILLIS);
                request.setReadTimeout(TIMEOUT_MILLIS);

                HttpResponse response = request.execute();
                try {
                    assertThat(response.getStatusCode()).isEqualTo(200);
                    assertThat(response.parseAsString()).isEqualTo("reported");
                } finally {
                    response.disconnect();
                }
            } finally {
                transport.shutdown();
            }
        }

        RecordedRequest request = recordedRequest.get();
        assertThat(request.method()).isEqualTo("REPORT");
        assertThat(request.path()).isEqualTo("/reports/current");
        assertThat(request.firstHeader("Content-Type")).isEqualTo("text/plain");
        assertThat(request.firstHeader("Transfer-Encoding")).isEqualTo("chunked");
        assertThat(request.bodyAsString()).isEqualTo("streamed report body");
    }

    @Test
    void executesStandardHttpMethodsWithoutRequestContent() throws Exception {
        List<String> expectedMethods = List.of(
                HttpMethods.DELETE,
                HttpMethods.HEAD,
                HttpMethods.PATCH,
                HttpMethods.PUT,
                HttpMethods.TRACE,
                HttpMethods.OPTIONS);
        List<String> observedMethods = Collections.synchronizedList(new ArrayList<>());
        try (TestHttpServer server = TestHttpServer.start(exchange -> {
            observedMethods.add(exchange.getRequestMethod());
            exchange.sendResponseHeaders(204, -1);
        })) {
            ApacheHttpTransport transport = new ApacheHttpTransport();
            try {
                HttpRequestFactory requestFactory = transport.createRequestFactory();
                for (String method : expectedMethods) {
                    HttpRequest request = requestFactory.buildRequest(
                            method, new GenericUrl(server.url("/methods/" + method, null)), null);
                    request.setConnectTimeout(TIMEOUT_MILLIS);
                    request.setReadTimeout(TIMEOUT_MILLIS);

                    HttpResponse response = request.execute();
                    try {
                        assertThat(response.getStatusCode()).isEqualTo(204);
                    } finally {
                        response.disconnect();
                    }
                }
            } finally {
                transport.shutdown();
            }
        }

        assertThat(observedMethods).containsExactlyElementsOf(expectedMethods);
    }

    @Test
    void rejectsContentOnHttpMethodWithoutEntitySupport() throws Exception {
        try (TestHttpServer server = TestHttpServer.start(
                exchange -> exchange.sendResponseHeaders(204, -1))) {
            ApacheHttpTransport transport = new ApacheHttpTransport();
            try {
                HttpRequest request = transport.createRequestFactory()
                        .buildRequest(
                                HttpMethods.GET,
                                new GenericUrl(server.url("/unexpected-content", null)),
                                ByteArrayContent.fromString("text/plain", "not valid for GET"));
                request.setConnectTimeout(TIMEOUT_MILLIS);
                request.setReadTimeout(TIMEOUT_MILLIS);

                assertThatThrownBy(request::execute)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("does not support GET requests with content");
            } finally {
                transport.shutdown();
            }
        }
    }

    @Test
    void exposesConfiguredApacheClientAndMtlsFlag() throws Exception {
        HttpClient httpClient = ApacheHttpTransport.newDefaultHttpClient();
        ApacheHttpTransport transport = new ApacheHttpTransport(httpClient, true);
        try {
            assertThat(transport.getHttpClient()).isSameAs(httpClient);
            assertThat(transport.isMtls()).isTrue();
            assertThat(transport.supportsMethod(HttpMethods.GET)).isTrue();
            assertThat(transport.supportsMethod("REPORT")).isTrue();
        } finally {
            transport.shutdown();
        }
    }

    @Test
    void customApacheClientInterceptorsCanModifyOutgoingRequests() throws Exception {
        AtomicReference<RecordedRequest> recordedRequest = new AtomicReference<>();
        try (TestHttpServer server = TestHttpServer.start(exchange -> {
            recordedRequest.set(RecordedRequest.from(exchange));
            exchange.sendResponseHeaders(204, -1);
        })) {
            HttpClient httpClient = ApacheHttpTransport.newDefaultHttpClientBuilder()
                    .addInterceptorFirst((HttpRequestInterceptor) (request, context) ->
                            request.addHeader("X-Apache-Interceptor", "applied"))
                    .build();
            ApacheHttpTransport transport = new ApacheHttpTransport(httpClient);
            try {
                HttpRequest request = transport.createRequestFactory()
                        .buildGetRequest(new GenericUrl(server.url("/interceptor", null)));
                request.setConnectTimeout(TIMEOUT_MILLIS);
                request.setReadTimeout(TIMEOUT_MILLIS);

                HttpResponse response = request.execute();
                try {
                    assertThat(response.getStatusCode()).isEqualTo(204);
                } finally {
                    response.disconnect();
                }
            } finally {
                transport.shutdown();
            }
        }

        RecordedRequest request = recordedRequest.get();
        assertThat(request.method()).isEqualTo(HttpMethods.GET);
        assertThat(request.path()).isEqualTo("/interceptor");
        assertThat(request.firstHeader("X-Apache-Interceptor")).isEqualTo("applied");
    }

    @Test
    void defaultClientRoutesRequestsThroughProxySelector() throws Exception {
        AtomicReference<RecordedRequest> recordedProxyRequest = new AtomicReference<>();
        try (TestHttpServer proxyServer = TestHttpServer.start(exchange -> {
            recordedProxyRequest.set(RecordedRequest.from(exchange));
            byte[] responseBody = "served by proxy".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.getResponseHeaders().set("X-Proxy", "selected");
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
        })) {
            ProxySelector originalProxySelector = ProxySelector.getDefault();
            FixedProxySelector proxySelector = new FixedProxySelector(
                    new InetSocketAddress("127.0.0.1", proxyServer.port()));
            ApacheHttpTransport transport = null;
            String targetUrl = "http://proxy-selector-target.example/proxy-target?via=selector";
            ProxySelector.setDefault(proxySelector);
            try {
                transport = new ApacheHttpTransport();
                HttpRequest request = transport.createRequestFactory()
                        .buildGetRequest(new GenericUrl(targetUrl));
                request.setConnectTimeout(TIMEOUT_MILLIS);
                request.setReadTimeout(TIMEOUT_MILLIS);

                HttpResponse response = request.execute();
                try {
                    assertThat(response.getStatusCode()).isEqualTo(200);
                    assertThat(response.getHeaders().getFirstHeaderStringValue("X-Proxy"))
                            .isEqualTo("selected");
                    assertThat(response.parseAsString()).isEqualTo("served by proxy");
                } finally {
                    response.disconnect();
                }
            } finally {
                if (transport != null) {
                    transport.shutdown();
                }
                ProxySelector.setDefault(originalProxySelector);
            }

            assertThat(proxySelector.selectedUris()).anySatisfy(uri -> {
                assertThat(uri.getScheme()).isEqualTo("http");
                assertThat(uri.getHost()).isEqualTo("proxy-selector-target.example");
            });
            assertThat(proxySelector.connectionFailure()).isNull();
        }

        RecordedRequest proxyRequest = recordedProxyRequest.get();
        assertThat(proxyRequest.method()).isEqualTo(HttpMethods.GET);
        assertThat(proxyRequest.path()).isEqualTo("/proxy-target");
        assertThat(proxyRequest.query()).isEqualTo("via=selector");
        assertThat(proxyRequest.firstHeader("Host"))
                .isEqualTo("proxy-selector-target.example");
    }

    private record RecordedRequest(
            String method,
            String path,
            String query,
            Map<String, List<String>> headers,
            byte[] body) {
        static RecordedRequest from(HttpExchange exchange) throws IOException {
            URI requestUri = exchange.getRequestURI();
            return new RecordedRequest(
                    exchange.getRequestMethod(),
                    requestUri.getPath(),
                    requestUri.getRawQuery(),
                    copyHeaders(exchange.getRequestHeaders()),
                    exchange.getRequestBody().readAllBytes());
        }

        String firstHeader(String name) {
            List<String> values = headers.get(name);
            return values == null || values.isEmpty() ? null : values.get(0);
        }

        String bodyAsString() {
            return new String(body, StandardCharsets.UTF_8);
        }

        private static Map<String, List<String>> copyHeaders(Headers source) {
            Map<String, List<String>> target = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            source.forEach((name, values) -> target.put(name, new ArrayList<>(values)));
            return target;
        }
    }

    private static final class TestHttpServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;

        private TestHttpServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static TestHttpServer start(HttpHandler handler) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            server.createContext("/", exchange -> {
                try (exchange) {
                    handler.handle(exchange);
                }
            });
            server.setExecutor(executor);
            server.start();
            return new TestHttpServer(server, executor);
        }

        String url(String path, String query) throws URISyntaxException {
            return new URI(
                    "http", null, "127.0.0.1", server.getAddress().getPort(), path, query, null)
                    .toString();
        }

        int port() {
            return server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private static final class FixedProxySelector extends ProxySelector {
        private final Proxy proxy;
        private final List<URI> selectedUris = Collections.synchronizedList(new ArrayList<>());
        private final AtomicReference<IOException> connectionFailure = new AtomicReference<>();

        private FixedProxySelector(InetSocketAddress proxyAddress) {
            this.proxy = new Proxy(Proxy.Type.HTTP, proxyAddress);
        }

        @Override
        public List<Proxy> select(URI uri) {
            selectedUris.add(uri);
            return List.of(proxy);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress socketAddress, IOException exception) {
            connectionFailure.set(exception);
        }

        List<URI> selectedUris() {
            return selectedUris;
        }

        IOException connectionFailure() {
            return connectionFailure.get();
        }
    }
}
