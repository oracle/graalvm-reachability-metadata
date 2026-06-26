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
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.http.codec.CodecCustomizer;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;
import org.springframework.boot.webclient.autoconfigure.WebClientCodecCustomizer;
import org.springframework.boot.webclient.autoconfigure.WebClientObservationAutoConfiguration;
import org.springframework.boot.webclient.autoconfigure.service.ReactiveHttpServiceClientAutoConfiguration;
import org.springframework.boot.webclient.observation.ObservationWebClientCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.web.reactive.function.client.ClientRequestObservationContext;
import org.springframework.web.reactive.function.client.ClientRequestObservationConvention;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

public class Spring_boot_webclientTest {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebClientAutoConfiguration.class));

    @Test
    void autoConfigurationsAreAdvertisedForSpringBootDiscovery() {
        ImportCandidates candidates = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader());

        assertThat(candidates.getCandidates()).contains(WebClientAutoConfiguration.class.getName(),
                WebClientObservationAutoConfiguration.class.getName(),
                ReactiveHttpServiceClientAutoConfiguration.class.getName());
    }

    @Test
    void webClientAutoConfigurationCreatesPrototypeBuilderWithConnectorAndCustomizers() throws IOException {
        try (TestHttpServer server = TestHttpServer.start(exchange -> {
            String body = "method=%s uri=%s header=%s".formatted(exchange.getRequestMethod(),
                    exchange.getRequestURI(), exchange.getRequestHeaders().getFirst("X-Auto-Configured"));
            send(exchange, HttpStatus.OK.value(), body);
        })) {
            this.contextRunner
                    .withBean(ClientHttpConnector.class, () -> jdkClientHttpConnector())
                    .withBean(WebClientCustomizer.class,
                            () -> builder -> builder.defaultHeader("X-Auto-Configured", "yes"))
                    .run(context -> {
                        assertThat(context).hasBean("webClientBuilder");
                        assertThat(context.getBean(WebClient.Builder.class))
                                .isNotSameAs(context.getBean(WebClient.Builder.class));

                        WebClient client = context.getBean(WebClient.Builder.class)
                                .baseUrl(server.url("/api"))
                                .build();

                        String response = client.get().uri("/items/{id}?q={query}", 42, "native image")
                                .retrieve()
                                .bodyToMono(String.class)
                                .block(REQUEST_TIMEOUT);

                        assertThat(response).isEqualTo("method=GET uri=/api/items/42?q=native%20image header=yes");
                    });
        }
    }

    @Test
    void webClientCodecCustomizerAppliesBootCodecCustomizersToClientBuilder() throws IOException {
        try (TestHttpServer server = TestHttpServer.start(exchange -> send(exchange, HttpStatus.OK.value(),
                "codec-customized"))) {
            AtomicBoolean codecCustomizerCalled = new AtomicBoolean();
            CodecCustomizer codecCustomizer = codecConfigurer -> {
                assertThat(codecConfigurer).isInstanceOf(ClientCodecConfigurer.class);
                codecConfigurer.defaultCodecs().maxInMemorySize(128 * 1024);
                codecCustomizerCalled.set(true);
            };
            WebClient.Builder builder = WebClient.builder()
                    .baseUrl(server.url(""))
                    .clientConnector(jdkClientHttpConnector());

            new WebClientCodecCustomizer(List.of(codecCustomizer)).customize(builder);
            WebClient client = builder.build();

            String body = client.get().uri("/codecs")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(REQUEST_TIMEOUT);

            assertThat(codecCustomizerCalled).isTrue();
            assertThat(body).isEqualTo("codec-customized");
        }
    }

    @Test
    void observationWebClientCustomizerRecordsClientObservationsWithConfiguredConvention() {
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        AtomicInteger startedObservations = new AtomicInteger();
        AtomicInteger stoppedObservations = new AtomicInteger();
        AtomicReference<String> observationName = new AtomicReference<>();
        AtomicReference<String> contextualName = new AtomicReference<>();
        observationRegistry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override
            public void onStart(Observation.Context context) {
                startedObservations.incrementAndGet();
                observationName.set(context.getName());
            }

            @Override
            public void onStop(Observation.Context context) {
                stoppedObservations.incrementAndGet();
                contextualName.set(context.getContextualName());
            }

            @Override
            public boolean supportsContext(Observation.Context context) {
                return context instanceof ClientRequestObservationContext;
            }
        });
        ClientRequestObservationConvention observationConvention = new ClientRequestObservationConvention() {
            @Override
            public String getName() {
                return "test.webclient.requests";
            }

            @Override
            public String getContextualName(ClientRequestObservationContext context) {
                return context.getRequest().method() + " " + context.getUriTemplate();
            }
        };
        WebClient.Builder builder = WebClient.builder()
                .baseUrl("https://example.test")
                .exchangeFunction(okExchangeFunction("observed"));

        new ObservationWebClientCustomizer(observationRegistry, observationConvention).customize(builder);
        WebClient client = builder.build();

        String body = client.get().uri("/orders/{id}", 99)
                .retrieve()
                .bodyToMono(String.class)
                .block(REQUEST_TIMEOUT);

        assertThat(body).isEqualTo("observed");
        assertThat(startedObservations).hasValue(1);
        assertThat(stoppedObservations).hasValue(1);
        assertThat(observationName).hasValue("test.webclient.requests");
        assertThat(contextualName).hasValue("GET https://example.test/orders/{id}");
    }

    @Test
    void webClientObservationAutoConfigurationCreatesCustomizerWhenObservationRegistryIsAvailable() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebClientObservationAutoConfiguration.class))
                .withBean(ObservationRegistry.class, ObservationRegistry::create)
                .run(context -> {
                    assertThat(context).hasSingleBean(ObservationWebClientCustomizer.class);
                    assertThat(context).hasSingleBean(ObservationRegistry.class);
                });
    }

    private static ExchangeFunction okExchangeFunction(String body) {
        return request -> Mono.just(ClientResponse.create(HttpStatus.OK).body(body).build());
    }

    private static JdkClientHttpConnector jdkClientHttpConnector() {
        JdkClientHttpConnector connector = new JdkClientHttpConnector(HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build());
        connector.setReadTimeout(REQUEST_TIMEOUT);
        return connector;
    }

    private static void send(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain;charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static final class TestHttpServer implements AutoCloseable {

        private final HttpServer server;

        private TestHttpServer(HttpServer server) {
            this.server = server;
        }

        private static TestHttpServer start(HttpHandler handler) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", exchange -> {
                try {
                    handler.handle(exchange);
                } catch (IOException ex) {
                    throw ex;
                } catch (RuntimeException ex) {
                    throw ex;
                }
            });
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
