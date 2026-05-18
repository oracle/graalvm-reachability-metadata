/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_reactor_netty;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.netty.resources.LoopResources;

import org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.reactor.netty.NettyServerCustomizer;
import org.springframework.boot.reactor.netty.autoconfigure.NettyReactiveWebServerFactoryCustomizer;
import org.springframework.boot.reactor.netty.autoconfigure.NettyServerProperties;
import org.springframework.boot.reactor.netty.autoconfigure.ReactorNettyProperties;
import org.springframework.boot.web.server.Compression;
import org.springframework.boot.web.server.GracefulShutdownResult;
import org.springframework.boot.web.server.Shutdown;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ReactorResourceFactory;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_reactor_nettyTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Test
    void nettyAndReactorNettyPropertiesStoreConfiguredValues() {
        NettyServerProperties nettyProperties = new NettyServerProperties();
        nettyProperties.setConnectionTimeout(Duration.ofSeconds(3));
        nettyProperties.setH2cMaxContentLength(DataSize.ofKilobytes(16));
        nettyProperties.setInitialBufferSize(DataSize.ofKilobytes(2));
        nettyProperties.setMaxInitialLineLength(DataSize.ofKilobytes(8));
        nettyProperties.setMaxKeepAliveRequests(7);
        nettyProperties.setValidateHeaders(false);
        nettyProperties.setIdleTimeout(Duration.ofSeconds(4));

        ReactorNettyProperties reactorNettyProperties = new ReactorNettyProperties();
        reactorNettyProperties.setShutdownQuietPeriod(Duration.ofMillis(250));

        assertThat(nettyProperties.getConnectionTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(nettyProperties.getH2cMaxContentLength()).isEqualTo(DataSize.ofKilobytes(16));
        assertThat(nettyProperties.getInitialBufferSize()).isEqualTo(DataSize.ofKilobytes(2));
        assertThat(nettyProperties.getMaxInitialLineLength()).isEqualTo(DataSize.ofKilobytes(8));
        assertThat(nettyProperties.getMaxKeepAliveRequests()).isEqualTo(7);
        assertThat(nettyProperties.isValidateHeaders()).isFalse();
        assertThat(nettyProperties.getIdleTimeout()).isEqualTo(Duration.ofSeconds(4));
        assertThat(reactorNettyProperties.getShutdownQuietPeriod()).isEqualTo(Duration.ofMillis(250));
    }

    @Test
    void factoryMaintainsServerCustomizersAndReportsImmediateShutdown() throws Exception {
        NettyReactiveWebServerFactory factory = loopbackFactory();
        NettyServerCustomizer first = server -> server;
        NettyServerCustomizer second = server -> server;

        factory.setServerCustomizers(List.of(first));
        factory.addServerCustomizers(second);

        assertThat(factory.getServerCustomizers()).containsExactly(first, second);

        WebServer webServer = factory.getWebServer(textHandler("unused"));
        CountDownLatch callbackInvoked = new CountDownLatch(1);
        GracefulShutdownResult[] result = new GracefulShutdownResult[1];
        webServer.shutDownGracefully((shutdownResult) -> {
            result[0] = shutdownResult;
            callbackInvoked.countDown();
        });

        assertThat(callbackInvoked.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(result[0]).isEqualTo(GracefulShutdownResult.IMMEDIATE);
        assertThat(webServer.getPort()).isEqualTo(-1);
    }

    @Test
    void httpHandlerBackedWebServerStartsOnEphemeralPortAndStopsCleanly() throws Exception {
        NettyReactiveWebServerFactory factory = loopbackFactory();
        AtomicInteger customizerInvocations = new AtomicInteger();
        factory.addServerCustomizers((server) -> {
            customizerInvocations.incrementAndGet();
            return server;
        });
        WebServer webServer = factory.getWebServer((request, response) -> {
            response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
            return write(response, request.getMethod().name() + " " + request.getURI().getPath());
        });

        try {
            webServer.start();
            HttpResponse<String> response = get(webServer, "/hello");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("GET /hello");
            assertThat(webServer.getPort()).isPositive();
            assertThat(customizerInvocations.get()).isEqualTo(1);
        }
        finally {
            webServer.stop();
        }

        assertThat(webServer.getPort()).isEqualTo(-1);
    }

    @Test
    void routeProvidersHandleSpecificRoutesAndFallBackToHttpHandler() throws Exception {
        NettyReactiveWebServerFactory factory = loopbackFactory();
        factory.addRouteProviders((routes) -> routes.get("/route", (request, response) -> response
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .sendString(Mono.just("route-provider"))));
        WebServer webServer = factory.getWebServer((request, response) -> {
            response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
            return write(response, "handler:" + request.getURI().getPath());
        });

        try {
            webServer.start();

            assertThat(get(webServer, "/route").body()).isEqualTo("route-provider");
            assertThat(get(webServer, "/fallback").body()).isEqualTo("handler:/fallback");
        }
        finally {
            webServer.stop();
        }
    }

    @Test
    void nativeForwardHeadersUpdateRequestUri() throws Exception {
        NettyReactiveWebServerFactory factory = loopbackFactory();
        factory.setUseForwardHeaders(true);
        WebServer webServer = factory.getWebServer((request, response) -> {
            response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
            return write(response, request.getURI().toString());
        });

        try {
            webServer.start();
            HttpResponse<String> response = get(webServer, "/forwarded?q=1", "Forwarded",
                    "proto=https;host=example.com:8443");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("https://example.com:8443/forwarded?q=1");
        }
        finally {
            webServer.stop();
        }
    }

    @Test
    void webServerUsesConfiguredReactorResourceFactoryLoopResources() throws Exception {
        NettyReactiveWebServerFactory factory = loopbackFactory();
        ReactorResourceFactory resourceFactory = new ReactorResourceFactory();
        resourceFactory.setUseGlobalResources(false);
        resourceFactory.setShutdownQuietPeriod(Duration.ZERO);
        resourceFactory.setShutdownTimeout(Duration.ofSeconds(5));
        resourceFactory.setLoopResources(LoopResources.create("boot-netty-test", 1, false));
        resourceFactory.afterPropertiesSet();
        factory.setResourceFactory(resourceFactory);
        WebServer webServer = factory.getWebServer((request, response) -> {
            response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
            return write(response, Thread.currentThread().getName());
        });

        try {
            webServer.start();
            HttpResponse<String> response = get(webServer, "/resources");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("boot-netty-test");
        }
        finally {
            try {
                webServer.stop();
            }
            finally {
                resourceFactory.destroy();
            }
        }
    }

    @Test
    void compressionProducesGzipResponseForEligibleTextBody() throws Exception {
        NettyReactiveWebServerFactory factory = loopbackFactory();
        Compression compression = new Compression();
        compression.setEnabled(true);
        compression.setMimeTypes(new String[] { MediaType.TEXT_PLAIN_VALUE });
        compression.setMinResponseSize(DataSize.ofBytes(1));
        factory.setCompression(compression);
        WebServer webServer = factory.getWebServer(textHandler("compressible response body"));

        try {
            webServer.start();
            HttpResponse<byte[]> response = getBytes(webServer, "/compressed", "Accept-Encoding", "gzip");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue(HttpHeaders.CONTENT_ENCODING)).isEqualTo(Optional.of("gzip"));
            assertThat(response.body()).isNotEmpty();
        }
        finally {
            webServer.stop();
        }
    }

    @Test
    void autoconfigurationCustomizerAppliesForwardHeadersAndRegistersNettyOptions() throws Exception {
        NettyReactiveWebServerFactory factory = loopbackFactory();
        ServerProperties serverProperties = new ServerProperties();
        serverProperties.setForwardHeadersStrategy(ServerProperties.ForwardHeadersStrategy.NATIVE);
        serverProperties.setMaxHttpRequestHeaderSize(DataSize.ofKilobytes(32));
        NettyServerProperties nettyProperties = new NettyServerProperties();
        nettyProperties.setConnectionTimeout(Duration.ofSeconds(2));
        nettyProperties.setIdleTimeout(Duration.ofSeconds(5));
        nettyProperties.setInitialBufferSize(DataSize.ofKilobytes(1));
        nettyProperties.setMaxInitialLineLength(DataSize.ofKilobytes(4));
        nettyProperties.setMaxKeepAliveRequests(3);
        nettyProperties.setValidateHeaders(true);

        NettyReactiveWebServerFactoryCustomizer customizer = new NettyReactiveWebServerFactoryCustomizer(
                new StandardEnvironment(), serverProperties, nettyProperties);
        assertThat(customizer.getOrder()).isZero();
        customizer.customize(factory);

        assertThat(factory.getServerCustomizers()).hasSizeGreaterThanOrEqualTo(4);

        WebServer webServer = factory.getWebServer((request, response) -> {
            response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
            return write(response, request.getURI().getScheme() + "://" + request.getURI().getAuthority());
        });
        try {
            webServer.start();
            HttpResponse<String> response = get(webServer, "/autoconfig", "X-Forwarded-Proto", "https",
                    "X-Forwarded-Host", "configured.example");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("https://configured.example");
        }
        finally {
            webServer.stop();
        }
    }

    private static NettyReactiveWebServerFactory loopbackFactory() {
        NettyReactiveWebServerFactory factory = new NettyReactiveWebServerFactory(0);
        factory.setAddress(InetAddress.getLoopbackAddress());
        factory.setLifecycleTimeout(Duration.ofSeconds(5));
        factory.setShutdown(Shutdown.IMMEDIATE);
        return factory;
    }

    private static HttpHandler textHandler(String body) {
        return (request, response) -> {
            response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
            return write(response, body);
        };
    }

    private static Mono<Void> write(ServerHttpResponse response, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        response.getHeaders().setContentLength(bytes.length);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private static HttpResponse<String> get(WebServer webServer, String path, String... headers) throws Exception {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
            HttpResponse.BodyHandler<String> bodyHandler = HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
            return client.send(request(webServer, path, headers), bodyHandler);
        }
    }

    private static HttpResponse<byte[]> getBytes(WebServer webServer, String path, String... headers) throws Exception {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
            return client.send(request(webServer, path, headers), HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    private static HttpRequest request(WebServer webServer, String path, String... headers) {
        URI uri = URI.create("http://127.0.0.1:" + webServer.getPort() + path);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET();
        for (int i = 0; i < headers.length; i += 2) {
            builder.header(headers[i], headers[i + 1]);
        }
        return builder.build();
    }
}
