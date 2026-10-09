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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.reactive.ClientHttpConnectorBuilder;
import org.springframework.boot.http.codec.CodecCustomizer;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.boot.webclient.autoconfigure.WebClientCodecCustomizer;
import org.springframework.boot.webclient.observation.ObservationWebClientCustomizer;
import org.springframework.web.reactive.function.client.DefaultClientRequestObservationConvention;
import org.springframework.web.reactive.function.client.WebClient;

public class Spring_boot_webclientTest {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void customizersConfigureCodecsHeadersAndObservation() throws IOException {
        AtomicInteger codecCustomizations = new AtomicInteger();
        AtomicInteger startedObservations = new AtomicInteger();
        AtomicInteger stoppedObservations = new AtomicInteger();
        try (TestHttpServer server = TestHttpServer.start(exchange -> {
            String body = exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
                    + exchange.getRequestHeaders().getFirst("X-WebClient");
            send(exchange, 200, body);
        })) {
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
            WebClient.Builder builder = WebClient.builder()
                    .baseUrl(server.url("/webclient"))
                    .clientConnector(ClientHttpConnectorBuilder.jdk().build(HttpClientSettings.defaults()
                            .withTimeouts(REQUEST_TIMEOUT, REQUEST_TIMEOUT)));
            WebClientCustomizer headerCustomizer = webClient -> webClient.defaultHeader("X-WebClient", "configured");

            new WebClientCodecCustomizer(List.of(codecCustomizer)).customize(builder);
            new ObservationWebClientCustomizer(observationRegistry,
                    new DefaultClientRequestObservationConvention()).customize(builder);
            headerCustomizer.customize(builder);
            String response = builder.build().get().retrieve().bodyToMono(String.class).block(REQUEST_TIMEOUT);

            assertThat(response).isEqualTo("GET /webclient configured");
            assertThat(codecCustomizations).hasValue(1);
            assertThat(startedObservations).hasValue(1);
            assertThat(stoppedObservations).hasValue(1);
        }
    }

    private static void send(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
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
