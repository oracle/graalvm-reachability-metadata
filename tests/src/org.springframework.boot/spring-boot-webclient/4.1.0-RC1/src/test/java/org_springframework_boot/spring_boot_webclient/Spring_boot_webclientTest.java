/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_webclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.autoconfigure.service.HttpServiceClientPropertiesAutoConfiguration;
import org.springframework.boot.http.client.reactive.ClientHttpConnectorBuilder;
import org.springframework.boot.http.codec.CodecCustomizer;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;
import org.springframework.boot.webclient.autoconfigure.WebClientCodecCustomizer;
import org.springframework.boot.webclient.autoconfigure.WebClientObservationAutoConfiguration;
import org.springframework.boot.webclient.autoconfigure.WebClientSsl;
import org.springframework.boot.webclient.autoconfigure.service.ReactiveHttpServiceClientAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.HttpServiceGroup;
import org.springframework.web.service.registry.HttpServiceGroupConfigurer;
import org.springframework.web.service.registry.HttpServiceProxyRegistry;

public class Spring_boot_webclientTest {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void autoConfigurationBuildsWebClientWithConnectorCodecsSslAndObservation() throws IOException {
        AtomicInteger codecCustomizations = new AtomicInteger();
        AtomicInteger startedObservations = new AtomicInteger();
        AtomicInteger stoppedObservations = new AtomicInteger();
        try (TestHttpServer server = TestHttpServer.start(exchange -> {
            String body = exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
                    + exchange.getRequestHeaders().getFirst("X-WebClient");
            send(exchange, 200, body);
        }); AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            ObservationRegistry observationRegistry = ObservationRegistry.create();
            observationRegistry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
                @Override
                public void onStart(Observation.Context context) {
                    startedObservations.incrementAndGet();
                }

                @Override
                public void onStop(Observation.Context context) {
                    stoppedObservations.incrementAndGet();
                }

                @Override
                public boolean supportsContext(Observation.Context context) {
                    return true;
                }
            });
            CodecCustomizer codecCustomizer = configurer -> {
                codecCustomizations.incrementAndGet();
                configurer.defaultCodecs().maxInMemorySize(16 * 1024);
            };
            context.registerBean(ClientHttpConnector.class,
                    () -> ClientHttpConnectorBuilder.jdk().build(HttpClientSettings.defaults()
                            .withTimeouts(REQUEST_TIMEOUT, REQUEST_TIMEOUT)));
            context.registerBean(ObservationRegistry.class, () -> observationRegistry);
            context.registerBean(WebClientCodecCustomizer.class,
                    () -> new WebClientCodecCustomizer(List.of(codecCustomizer)));
            context.registerBean(SslBundles.class, TestSslBundles::new);
            context.register(WebClientAutoConfiguration.class, WebClientObservationAutoConfiguration.class);
            context.refresh();

            WebClient.Builder builder = context.getBean(WebClient.Builder.class)
                    .baseUrl(server.url("/webclient"))
                    .defaultHeader("X-WebClient", "configured");
            String response = builder.build().get().retrieve().bodyToMono(String.class).block(REQUEST_TIMEOUT);

            assertThat(response).isEqualTo("GET /webclient configured");
            assertThat(codecCustomizations).hasValue(1);
            assertThat(startedObservations).hasValue(1);
            assertThat(stoppedObservations).hasValue(1);

            WebClient.Builder sslBuilder = context.getBean(WebClient.Builder.class)
                    .baseUrl(server.url("/ssl"));
            WebClientSsl webClientSsl = context.getBean(WebClientSsl.class);
            webClientSsl.fromBundle("test").accept(sslBuilder);
            String sslResponse = sslBuilder.build().get().retrieve().bodyToMono(String.class).block(REQUEST_TIMEOUT);

            assertThat(sslResponse).isEqualTo("GET /ssl null");
            assertThat(startedObservations).hasValue(2);
            assertThat(stoppedObservations).hasValue(2);
        }
    }

    @Test
    void reactiveHttpServiceConfigurersApplyPropertiesAndWebClientCustomizers() throws IOException {
        try (TestHttpServer server = TestHttpServer.start(exchange -> {
            String body = exchange.getRequestURI() + " "
                    + exchange.getRequestHeaders().getFirst("X-Group-Customizer");
            send(exchange, 200, body);
        }); AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                    Map.of("spring.http.serviceclient.orders.base-url", server.url("/configured"))));
            context.registerBean(HttpServiceProxyRegistry.class, EmptyHttpServiceProxyRegistry::new);
            context.registerBean(SslBundles.class, TestSslBundles::new);
            context.registerBean(ClientHttpConnector.class,
                    () -> ClientHttpConnectorBuilder.jdk().build(HttpClientSettings.defaults()
                            .withTimeouts(REQUEST_TIMEOUT, REQUEST_TIMEOUT)));
            context.registerBean(WebClientCustomizer.class,
                    () -> builder -> builder.defaultHeader("X-Group-Customizer", "applied"));
            context.register(HttpServiceClientPropertiesAutoConfiguration.class,
                    WebClientAutoConfiguration.class, ReactiveHttpServiceClientAutoConfiguration.class);
            context.refresh();

            List<WebClientHttpServiceGroupConfigurer> configurers = new ArrayList<>(
                    context.getBeansOfType(WebClientHttpServiceGroupConfigurer.class).values());
            configurers.sort((left, right) -> Integer.compare(left.getOrder(), right.getOrder()));
            assertThat(configurers).hasSize(2);
            assertThat(configurers).extracting(Ordered::getOrder)
                    .containsExactly(Integer.MIN_VALUE, 0);

            RecordingGroups groups = new RecordingGroups();
            for (WebClientHttpServiceGroupConfigurer configurer : configurers) {
                configurer.configureGroups(groups);
            }
            String response = groups.builder.build().get().retrieve().bodyToMono(String.class).block(REQUEST_TIMEOUT);

            assertThat(response).isEqualTo("/configured " + "applied");
        }
    }

    @Test
    void reactiveHttpServicePropertiesInsertConfiguredApiVersionHeader() throws IOException {
        AtomicReference<String> receivedApiVersion = new AtomicReference<>();
        try (TestHttpServer server = TestHttpServer.start(exchange -> {
            receivedApiVersion.set(exchange.getRequestHeaders().getFirst("X-API-Version"));
            send(exchange, 200, "ok");
        }); AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                    Map.of("spring.http.serviceclient.orders.base-url", server.url("/versioned"),
                            "spring.http.serviceclient.orders.apiversion.default", "v2",
                            "spring.http.serviceclient.orders.apiversion.insert.header", "X-API-Version")));
            context.registerBean(HttpServiceProxyRegistry.class, EmptyHttpServiceProxyRegistry::new);
            context.registerBean(SslBundles.class, TestSslBundles::new);
            context.registerBean(ClientHttpConnector.class,
                    () -> ClientHttpConnectorBuilder.jdk().build(HttpClientSettings.defaults()
                            .withTimeouts(REQUEST_TIMEOUT, REQUEST_TIMEOUT)));
            context.register(HttpServiceClientPropertiesAutoConfiguration.class,
                    WebClientAutoConfiguration.class, ReactiveHttpServiceClientAutoConfiguration.class);
            context.refresh();

            List<WebClientHttpServiceGroupConfigurer> configurers = new ArrayList<>(
                    context.getBeansOfType(WebClientHttpServiceGroupConfigurer.class).values());
            RecordingGroups groups = new RecordingGroups();
            configurers.forEach((configurer) -> configurer.configureGroups(groups));

            String response = groups.builder.build().get().retrieve().bodyToMono(String.class).block(REQUEST_TIMEOUT);

            assertThat(response).isEqualTo("ok");
            assertThat(receivedApiVersion).hasValue("v2");
        }
    }

    private static void send(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static final class TestSslBundles implements SslBundles {

        @Override
        public SslBundle getBundle(String name) {
            assertThat(name).isEqualTo("test");
            return SslBundle.systemDefault();
        }

        @Override
        public void addBundleUpdateHandler(String name, java.util.function.Consumer<SslBundle> handler) {
        }

        @Override
        public void addBundleRegisterHandler(java.util.function.BiConsumer<String, SslBundle> handler) {
        }

        @Override
        public List<String> getBundleNames() {
            return List.of("test");
        }
    }

    private static final class EmptyHttpServiceProxyRegistry implements HttpServiceProxyRegistry {

        @Override
        public <P> P getClient(Class<P> serviceType) {
            return null;
        }

        @Override
        public <P> P getClient(String groupName, Class<P> serviceType) {
            return null;
        }

        @Override
        public Set<String> getGroupNames() {
            return Set.of();
        }

        @Override
        public Set<Class<?>> getClientTypesInGroup(String groupName) {
            return Set.of();
        }
    }

    private static final class RecordingGroups implements HttpServiceGroupConfigurer.Groups<WebClient.Builder> {

        private final HttpServiceGroup group = new HttpServiceGroup() {
            @Override
            public String name() {
                return "orders";
            }

            @Override
            public Set<Class<?>> httpServiceTypes() {
                return Set.of();
            }

            @Override
            public ClientType clientType() {
                return ClientType.WEB_CLIENT;
            }
        };

        private WebClient.Builder builder = WebClient.builder();

        @Override
        public HttpServiceGroupConfigurer.Groups<WebClient.Builder> filterByName(String... names) {
            return this;
        }

        @Override
        public HttpServiceGroupConfigurer.Groups<WebClient.Builder> filter(
                java.util.function.Predicate<HttpServiceGroup> predicate) {
            return this;
        }

        @Override
        public void forEachClient(HttpServiceGroupConfigurer.ClientCallback<WebClient.Builder> callback) {
            callback.withClient(this.group, this.builder);
        }

        @Override
        public void forEachClient(HttpServiceGroupConfigurer.InitializingClientCallback<WebClient.Builder> callback) {
            this.builder = callback.initClient(this.group);
        }

        @Override
        public void forEachProxyFactory(HttpServiceGroupConfigurer.ProxyFactoryCallback callback) {
        }

        @Override
        public void forEachGroup(HttpServiceGroupConfigurer.GroupCallback<WebClient.Builder> callback) {
        }
    }

    private static final class TestHttpServer implements AutoCloseable {

        private final HttpServer server;

        private TestHttpServer(HttpServer server) {
            this.server = server;
        }

        private static TestHttpServer start(HttpHandler handler) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", handler);
            server.start();
            return new TestHttpServer(server);
        }

        private String url(String path) {
            return "http://127.0.0.1:" + this.server.getAddress().getPort() + path;
        }

        @Override
        public void close() {
            this.server.stop(0);
        }
    }
}
