/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_resttestclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.http.converter.autoconfigure.ClientHttpMessageConvertersCustomizer;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.resttestclient.autoconfigure.RestTestClientBuilderCustomizer;
import org.springframework.boot.resttestclient.autoconfigure.SpringBootRestTestClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

public class Spring_boot_resttestclientTest {
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void testRestTemplatePerformsGetPostAndFaultTolerantExchange() throws IOException {
        try (TestHttpServer server = TestHttpServer.start(Spring_boot_resttestclientTest::handleRequest)) {
            AtomicInteger customizedTemplates = new AtomicInteger();
            RestTemplateBuilder builder = new RestTemplateBuilder()
                    .requestFactory(Spring_boot_resttestclientTest::requestFactory)
                    .baseUri(server.url(""))
                    .defaultHeader("X-Builder", "configured")
                    .additionalCustomizers(restTemplate -> {
                        customizedTemplates.incrementAndGet();
                        restTemplate.getInterceptors().add((request, body, execution) -> {
                            request.getHeaders().set("X-Customizer", "applied");
                            return execution.execute(request, body);
                        });
                    });
            TestRestTemplate template = new TestRestTemplate(builder);

            ResponseEntity<String> getResponse = template.getForEntity("/greeting/{name}", String.class, "native");
            String postResponse = template.postForObject("/echo", "request-body", String.class);
            ResponseEntity<String> errorResponse = template.exchange("/unavailable", HttpMethod.GET,
                    HttpEntity.EMPTY, String.class);

            assertThat(customizedTemplates).hasValue(1);
            assertThat(template.getRootUri()).isEqualTo(server.url(""));
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getResponse.getBody())
                    .isEqualTo("GET /greeting/native builder=configured customizer=applied");
            assertThat(postResponse).isEqualTo("POST /echo body=request-body");
            assertThat(errorResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(errorResponse.getBody()).isEqualTo("temporarily unavailable");
        }
    }

    @Test
    void restTestClientPerformsGetAndPostWithBootBuilderCustomizers() throws IOException {
        try (TestHttpServer server = TestHttpServer.start(Spring_boot_resttestclientTest::handleRequest)) {
            AtomicBoolean messageConvertersCustomized = new AtomicBoolean();
            ClientHttpMessageConvertersCustomizer converterCustomizer = converters -> {
                messageConvertersCustomized.set(true);
                converters.withStringConverter(new StringHttpMessageConverter(StandardCharsets.UTF_8));
            };
            RestTestClient.Builder<?> builder = RestTestClient.bindToServer(requestFactory())
                    .baseUrl(server.url(""));
            RestTestClientBuilderCustomizer headerCustomizer = candidate -> candidate.defaultHeader(
                    "X-Rest-Test-Client", "configured");
            headerCustomizer.customize(builder);
            new SpringBootRestTestClientBuilderCustomizer(List.of(converterCustomizer)).customize(builder);
            RestTestClient client = builder.build();

            client.get()
                    .uri("/client/{id}", 42)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .isEqualTo("GET /client/42 client=configured");

            client.post()
                    .uri("/echo")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("client-body")
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(String.class)
                    .isEqualTo("POST /echo body=client-body");

            assertThat(messageConvertersCustomized).isTrue();
        }
    }

    @Test
    void autoConfigurationCreatesMockMvcBoundRestTestClient() {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(MockMvcClientConfiguration.class);
            context.refresh();

            RestTestClient client = context.getBean(RestTestClient.class);
            client.get()
                    .uri("/auto-configured")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .isEqualTo("created by auto-configuration");
        }
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(HTTP_TIMEOUT);
        requestFactory.setReadTimeout(HTTP_TIMEOUT);
        return requestFactory;
    }

    private static void handleRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.startsWith("/greeting/")) {
            send(exchange, HttpStatus.OK, "%s %s builder=%s customizer=%s".formatted(exchange.getRequestMethod(),
                    exchange.getRequestURI(), exchange.getRequestHeaders().getFirst("X-Builder"),
                    exchange.getRequestHeaders().getFirst("X-Customizer")));
            return;
        }
        if (path.startsWith("/client/")) {
            send(exchange, HttpStatus.OK, "%s %s client=%s".formatted(exchange.getRequestMethod(),
                    exchange.getRequestURI(), exchange.getRequestHeaders().getFirst("X-Rest-Test-Client")));
            return;
        }
        if ("/echo".equals(path)) {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            HttpStatus status = exchange.getRequestHeaders().containsKey("X-Rest-Test-Client")
                    ? HttpStatus.CREATED
                    : HttpStatus.OK;
            send(exchange, status, exchange.getRequestMethod() + " /echo body=" + requestBody);
            return;
        }
        if ("/unavailable".equals(path)) {
            send(exchange, HttpStatus.SERVICE_UNAVAILABLE, "temporarily unavailable");
            return;
        }
        send(exchange, HttpStatus.NOT_FOUND, "not found");
    }

    private static void send(HttpExchange exchange, HttpStatus status, String body) throws IOException {
        byte[] responseBody = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain;charset=UTF-8");
        exchange.sendResponseHeaders(status.value(), responseBody.length);
        exchange.getResponseBody().write(responseBody);
        exchange.close();
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigureRestTestClient
    public static class MockMvcClientConfiguration {
        @Bean
        MockMvc mockMvc() {
            return MockMvcBuilders.standaloneSetup(new AutoConfiguredController()).build();
        }
    }

    @RestController
    public static class AutoConfiguredController {
        @GetMapping("/auto-configured")
        String autoConfigured() {
            return "created by auto-configuration";
        }
    }

    private static final class TestHttpServer implements AutoCloseable {
        private final HttpServer server;

        private TestHttpServer(HttpServer server) {
            this.server = server;
        }

        private static TestHttpServer start(HttpHandler handler) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
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
