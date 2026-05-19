/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_restclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.service.HttpServiceClientPropertiesAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.ClientHttpMessageConvertersCustomizer;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.restclient.RestTemplateCustomizer;
import org.springframework.boot.restclient.RestTemplateRequestCustomizer;
import org.springframework.boot.restclient.RootUriBuilderFactory;
import org.springframework.boot.restclient.autoconfigure.HttpMessageConvertersRestClientCustomizer;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientBuilderConfigurer;
import org.springframework.boot.restclient.autoconfigure.RestClientObservationAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestTemplateAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestTemplateBuilderConfigurer;
import org.springframework.boot.restclient.autoconfigure.RestTemplateObservationAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.service.HttpServiceClientAutoConfiguration;
import org.springframework.boot.restclient.observation.ObservationRestClientCustomizer;
import org.springframework.boot.restclient.observation.ObservationRestTemplateCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.client.observation.ClientRequestObservationConvention;
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.registry.HttpServiceGroup.ClientType;
import org.springframework.web.service.registry.HttpServiceProxyRegistry;
import org.springframework.web.service.registry.ImportHttpServices;
import org.springframework.web.util.DefaultUriBuilderFactory;

public class Spring_boot_restclientTest {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);

    private final ApplicationContextRunner restClientContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class));

    private final ApplicationContextRunner restTemplateContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestTemplateAutoConfiguration.class));

    @Test
    void autoConfigurationsAreAdvertisedForSpringBootDiscovery() {
        ImportCandidates candidates = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader());

        assertThat(candidates.getCandidates()).contains(RestClientAutoConfiguration.class.getName(),
                RestClientObservationAutoConfiguration.class.getName(), RestTemplateAutoConfiguration.class.getName(),
                RestTemplateObservationAutoConfiguration.class.getName(),
                HttpServiceClientAutoConfiguration.class.getName());
    }

    @Test
    void httpServiceClientAutoConfigurationBuildsRestClientBackedServiceProxy() throws IOException {
        try (TestHttpServer server = TestHttpServer.start(exchange -> {
            Headers requestHeaders = exchange.getRequestHeaders();
            String response = "method=%s, uri=%s, customizer=%s".formatted(exchange.getRequestMethod(),
                    exchange.getRequestURI(), requestHeaders.getFirst("X-Service-Customizer"));
            send(exchange, HttpStatus.OK.value(), response);
        })) {
            new ApplicationContextRunner()
                    .withUserConfiguration(GreetingHttpServiceConfiguration.class)
                    .withConfiguration(AutoConfigurations.of(HttpServiceClientPropertiesAutoConfiguration.class,
                            ImperativeHttpClientAutoConfiguration.class, RestClientAutoConfiguration.class,
                            HttpServiceClientAutoConfiguration.class))
                    .withPropertyValues("spring.http.serviceclient.greeting.base-url=" + server.url("/api"))
                    .withBean(RestClientCustomizer.class,
                            () -> builder -> builder.defaultHeader("X-Service-Customizer", "applied")
                                    .requestFactory(requestFactory()))
                    .run(context -> {
                        assertThat(context).hasSingleBean(HttpServiceProxyRegistry.class);
                        HttpServiceProxyRegistry registry = context.getBean(HttpServiceProxyRegistry.class);

                        GreetingHttpService client = registry.getClient(GreetingHttpService.class);
                        String body = client.greeting("boot", "done");

                        assertThat(registry.getGroupNames()).containsExactly("greeting");
                        assertThat(registry.getClientTypesInGroup("greeting"))
                                .containsExactly(GreetingHttpService.class);
                        assertThat(body).isEqualTo("method=GET, uri=/api/greeting/boot?punctuation=done, "
                                + "customizer=applied");
                    });
        }
    }

    @Test
    void restClientAutoConfigurationCreatesPrototypeBuilderAndAppliesCustomizers() throws IOException {
        try (TestHttpServer server = TestHttpServer.start(exchange -> send(exchange, HttpStatus.OK.value(),
                exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
                        + exchange.getRequestHeaders().getFirst("X-Auto-Configured")))) {
            this.restClientContextRunner
                    .withBean(RestClientCustomizer.class,
                            () -> builder -> builder.baseUrl(server.url("/auto"))
                                    .defaultHeader("X-Auto-Configured", "true")
                                    .requestFactory(requestFactory()))
                    .run(context -> {
                        assertThat(context).hasSingleBean(RestClientBuilderConfigurer.class);
                        assertThat(context).hasSingleBean(RestClient.Builder.class);
                        RestClient.Builder firstBuilder = context.getBean(RestClient.Builder.class);
                        RestClient.Builder secondBuilder = context.getBean(RestClient.Builder.class);
                        RestClient restClient = firstBuilder.build();

                        String body = restClient.get().uri("/orders/{id}", 99).retrieve().body(String.class);

                        assertThat(firstBuilder).isNotSameAs(secondBuilder);
                        assertThat(body).isEqualTo("GET /auto/orders/99 true");
                    });
        }
    }

    @Test
    void restTemplateAutoConfigurationCreatesBuilderWithConvertersAndCustomizers() throws IOException {
        try (TestHttpServer server = TestHttpServer.start(exchange -> {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String response = "body=" + requestBody + ", template="
                    + exchange.getRequestHeaders().getFirst("X-Template-Customizer") + ", request="
                    + exchange.getRequestHeaders().getFirst("X-Request-Customizer");
            send(exchange, HttpStatus.OK.value(), response);
        })) {
            AtomicBoolean convertersCustomizerCalled = new AtomicBoolean();
            RestTemplateRequestCustomizer<ClientHttpRequest> requestCustomizer = request -> request.getHeaders()
                    .set("X-Request-Customizer", "called");
            ClientHttpMessageConvertersCustomizer convertersCustomizer = converters -> {
                convertersCustomizerCalled.set(true);
                converters.withStringConverter(new StringHttpMessageConverter(StandardCharsets.UTF_8));
            };

            this.restTemplateContextRunner
                    .withBean(ClientHttpMessageConvertersCustomizer.class, () -> convertersCustomizer)
                    .withBean(RestTemplateRequestCustomizer.class, () -> requestCustomizer)
                    .withBean(RestTemplateCustomizer.class,
                            () -> template -> template.getInterceptors().add((request, body, execution) -> {
                                request.getHeaders().set("X-Template-Customizer", "called");
                                return execution.execute(request, body);
                            }))
                    .run(context -> {
                        assertThat(context).hasSingleBean(RestTemplateBuilderConfigurer.class);
                        assertThat(context).hasSingleBean(RestTemplateBuilder.class);
                        RestTemplateBuilder builder = context.getBean(RestTemplateBuilder.class)
                                .requestFactory(Spring_boot_restclientTest::requestFactory)
                                .rootUri(server.url("/root"));
                        RestTemplate restTemplate = builder.build();

                        String body = restTemplate.postForObject("/echo", "payload", String.class);

                        assertThat(convertersCustomizerCalled).isTrue();
                        assertThat(body).isEqualTo("body=payload, template=called, request=called");
                    });
        }
    }

    @Test
    void restTemplateBuilderInstantiatesCustomTemplateAndRequestFactoryTypes() {
        AtomicBoolean customizerCalled = new AtomicBoolean();

        InstrumentedRestTemplate restTemplate = new RestTemplateBuilder(template -> {
            customizerCalled.set(true);
            assertThat(template).isInstanceOf(InstrumentedRestTemplate.class);
        })
                .requestFactory(SimpleClientHttpRequestFactory.class)
                .additionalMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8))
                .build(InstrumentedRestTemplate.class);

        assertThat(customizerCalled).isTrue();
        assertThat(restTemplate).isInstanceOf(InstrumentedRestTemplate.class);
        assertThat(restTemplate.getRequestFactory()).isInstanceOf(SimpleClientHttpRequestFactory.class);
        assertThat(restTemplate.getMessageConverters()).anySatisfy(
                converter -> assertThat(converter).isInstanceOf(StringHttpMessageConverter.class));
    }

    @Test
    void restTemplateBuilderAppliesRootUriAuthenticationHeadersInterceptorsAndRequestCustomizers() throws IOException {
        try (TestHttpServer server = TestHttpServer.start(exchange -> {
            Headers requestHeaders = exchange.getRequestHeaders();
            String response = """
                    method=%s
                    uri=%s
                    authorization=%s
                    default=%s
                    interceptor=%s
                    requestCustomizer=%s
                    """.formatted(exchange.getRequestMethod(), exchange.getRequestURI(),
                    requestHeaders.getFirst("Authorization"), requestHeaders.getFirst("X-Default"),
                    requestHeaders.getFirst("X-Interceptor"), requestHeaders.getFirst("X-Request-Customizer"));
            send(exchange, HttpStatus.OK.value(), response);
        })) {
            RestTemplateRequestCustomizer<ClientHttpRequest> requestCustomizer = request -> request.getHeaders()
                    .set("X-Request-Customizer", "customized");

            RestTemplate restTemplate = baseRestTemplateBuilder()
                    .rootUri(server.url("/api"))
                    .basicAuthentication("user", "password")
                    .defaultHeader("X-Default", "default-value")
                    .additionalInterceptors((request, body, execution) -> {
                        request.getHeaders().set("X-Interceptor", "intercepted");
                        return execution.execute(request, body);
                    })
                    .additionalRequestCustomizers(requestCustomizer)
                    .build();

            String body = restTemplate.getForObject("/items/{id}?q={query}", String.class, 42, "native image");

            assertThat(body).contains("method=GET");
            assertThat(body).contains("uri=/api/items/42?q=native%20image");
            assertThat(body).contains("authorization=Basic dXNlcjpwYXNzd29yZA==");
            assertThat(body).contains("default=default-value");
            assertThat(body).contains("interceptor=intercepted");
            assertThat(body).contains("requestCustomizer=customized");
        }
    }

    @Test
    void restTemplateBuilderConfiguresMessageConvertersErrorHandlerAndCustomizers() throws IOException {
        try (TestHttpServer server = TestHttpServer.start(exchange -> {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String response = "body=" + requestBody + ", customizer="
                    + exchange.getRequestHeaders().getFirst("X-Customizer");
            send(exchange, HttpStatus.INTERNAL_SERVER_ERROR.value(), response);
        })) {
            AtomicBoolean templateCustomizerCalled = new AtomicBoolean();
            ResponseErrorHandler ignoreServerErrors = new ResponseErrorHandler() {
                @Override
                public boolean hasError(ClientHttpResponse response) {
                    return false;
                }
            };

            RestTemplate restTemplate = baseRestTemplateBuilder()
                    .messageConverters(List.of(new StringHttpMessageConverter(StandardCharsets.UTF_8)))
                    .errorHandler(ignoreServerErrors)
                    .additionalCustomizers(template -> {
                        templateCustomizerCalled.set(true);
                        template.getInterceptors().add((request, body, execution) -> {
                            request.getHeaders().set("X-Customizer", "called");
                            return execution.execute(request, body);
                        });
                    })
                    .build();

            String body = restTemplate.postForObject(server.url("/echo"), "payload", String.class);

            assertThat(templateCustomizerCalled).isTrue();
            assertThat(restTemplate.getMessageConverters()).hasSize(1)
                    .first()
                    .isInstanceOf(StringHttpMessageConverter.class);
            assertThat(restTemplate.getErrorHandler()).isSameAs(ignoreServerErrors);
            assertThat(body).isEqualTo("body=payload, customizer=called");
        }
    }

    @Test
    void restTemplateBuilderAppliesRedirectSettingsToRequestFactoryBuilder() throws IOException {
        AtomicInteger targetRequests = new AtomicInteger();
        try (TestHttpServer server = TestHttpServer.start(exchange -> {
            if ("/redirect".equals(exchange.getRequestURI().getPath())) {
                sendRedirect(exchange, "http://127.0.0.1:" + exchange.getLocalAddress().getPort() + "/target");
                return;
            }
            targetRequests.incrementAndGet();
            send(exchange, HttpStatus.OK.value(), "target=" + exchange.getRequestURI());
        })) {
            RestTemplate followingRestTemplate = new RestTemplateBuilder()
                    .requestFactoryBuilder(ClientHttpRequestFactoryBuilder.simple())
                    .connectTimeout(REQUEST_TIMEOUT)
                    .readTimeout(REQUEST_TIMEOUT)
                    .redirects(HttpRedirects.FOLLOW)
                    .build();

            ResponseEntity<String> followedResponse = followingRestTemplate.getForEntity(server.url("/redirect"),
                    String.class);

            assertThat(followedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(followedResponse.getBody()).isEqualTo("target=/target");
            assertThat(targetRequests).hasValue(1);

            RestTemplate nonFollowingRestTemplate = new RestTemplateBuilder()
                    .requestFactoryBuilder(ClientHttpRequestFactoryBuilder.simple())
                    .connectTimeout(REQUEST_TIMEOUT)
                    .readTimeout(REQUEST_TIMEOUT)
                    .redirects(HttpRedirects.DONT_FOLLOW)
                    .build();

            ResponseEntity<String> redirectResponse = nonFollowingRestTemplate.getForEntity(server.url("/redirect"),
                    String.class);

            assertThat(redirectResponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(targetRequests).hasValue(1);
        }
    }

    @Test
    void rootUriBuilderFactoryExpandsRootRelativeTemplatesAndLeavesAbsoluteTemplatesUntouched() {
        RootUriBuilderFactory factory = new RootUriBuilderFactory("https://api.example.test/v1",
                new DefaultUriBuilderFactory());

        assertThat(factory.getRootUri()).isEqualTo("https://api.example.test/v1");
        assertThat(factory.expand("/orders/{id}", 7)).hasToString("https://api.example.test/v1/orders/7");
        assertThat(factory.expand("/search/{term}", Map.of("term", "spring boot")))
                .hasToString("https://api.example.test/v1/search/spring%20boot");
        assertThat(factory.expand("https://other.example.test/{id}", 9)).hasToString("https://other.example.test/9");
        assertThat(factory.uriString("/orders").queryParam("status", "new").build())
                .hasToString("https://api.example.test/v1/orders?status=new");
        assertThat(factory.builder().scheme("https").host("builder.example.test").path("/health").build())
                .hasToString("https://builder.example.test/health");
    }

    @Test
    void restClientBuilderConfigurerAndMessageConverterCustomizerBuildFunctionalClient() throws IOException {
        try (TestHttpServer server = TestHttpServer.start(exchange -> {
            String response = exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
                    + exchange.getRequestHeaders().getFirst("X-RestClient");
            send(exchange, HttpStatus.OK.value(), response);
        })) {
            AtomicBoolean convertersCustomizerCalled = new AtomicBoolean();
            RestClient.Builder builder = RestClient.builder()
                    .baseUrl(server.url("/rest"))
                    .defaultHeader("X-RestClient", "configured");
            ClientHttpMessageConvertersCustomizer convertersCustomizer = converters -> {
                convertersCustomizerCalled.set(true);
                converters.withStringConverter(new StringHttpMessageConverter(StandardCharsets.UTF_8));
            };

            new RestClientBuilderConfigurer().configure(builder);
            new HttpMessageConvertersRestClientCustomizer(convertersCustomizer).customize(builder);
            RestClient restClient = builder.build();

            String body = restClient.get().uri("/hello/{name}", "boot")
                    .exchange((request, response) -> new String(response.getBody().readAllBytes(),
                            StandardCharsets.UTF_8));

            assertThat(convertersCustomizerCalled).isTrue();
            assertThat(body).isEqualTo("GET /rest/hello/boot configured");
        }
    }

    @Test
    void observationRestClientCustomizerAppliesRegistryAndConventionToRestClient() throws IOException {
        try (TestHttpServer server = TestHttpServer.start(
                exchange -> send(exchange, HttpStatus.OK.value(), "observed"))) {
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
                public boolean supportsContext(Observation.Context context) {
                    return context instanceof ClientRequestObservationContext;
                }

                @Override
                public String getName() {
                    return "test.rest.client";
                }

                @Override
                public String getContextualName(ClientRequestObservationContext context) {
                    return "test request";
                }
            };

            RestClient.Builder builder = RestClient.builder()
                    .baseUrl(server.url("/observed"))
                    .requestFactory(requestFactory());
            new ObservationRestClientCustomizer(observationRegistry, observationConvention).customize(builder);
            RestClient restClient = builder.build();

            String body = restClient.get().uri("/resource").retrieve().body(String.class);

            assertThat(body).isEqualTo("observed");
            assertThat(startedObservations).hasValue(1);
            assertThat(stoppedObservations).hasValue(1);
            assertThat(observationName).hasValue("test.rest.client");
            assertThat(contextualName).hasValue("test request");
        }
    }

    @Test
    void restTemplateConfigurerAndObservationCustomizerApplyToTemplateBuilder() {
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        ClientRequestObservationConvention observationConvention = new DefaultClientRequestObservationConvention();
        ObservationRestTemplateCustomizer observationCustomizer =
                new ObservationRestTemplateCustomizer(observationRegistry, observationConvention);

        RestTemplateBuilder configuredBuilder = new RestTemplateBuilderConfigurer()
                .configure(baseRestTemplateBuilder().additionalCustomizers(observationCustomizer));
        RestTemplate restTemplate = configuredBuilder.build();

        assertThat(restTemplate.getObservationRegistry()).isSameAs(observationRegistry);
        assertThat(restTemplate.getObservationConvention()).isSameAs(observationConvention);
        assertThat(configuredBuilder.buildRequestFactory()).isInstanceOf(SimpleClientHttpRequestFactory.class);
        assertThat(configuredBuilder.requestFactoryBuilder()).isNotNull();
    }

    private static RestTemplateBuilder baseRestTemplateBuilder() {
        return new RestTemplateBuilder().requestFactory(Spring_boot_restclientTest::requestFactory);
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(REQUEST_TIMEOUT);
        requestFactory.setReadTimeout(REQUEST_TIMEOUT);
        return requestFactory;
    }

    private static void send(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain;charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void sendRedirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(HttpStatus.FOUND.value(), -1);
        exchange.close();
    }

    @Configuration(proxyBeanMethods = false)
    @ImportHttpServices(group = "greeting", types = GreetingHttpService.class, clientType = ClientType.REST_CLIENT)
    public static class GreetingHttpServiceConfiguration {
    }

    public interface GreetingHttpService {

        @GetExchange("/greeting/{name}")
        String greeting(@PathVariable("name") String name, @RequestParam("punctuation") String punctuation);

    }

    public static final class InstrumentedRestTemplate extends RestTemplate {
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
