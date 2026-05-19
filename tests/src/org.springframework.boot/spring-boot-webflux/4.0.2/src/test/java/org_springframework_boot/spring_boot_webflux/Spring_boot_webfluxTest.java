/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_webflux;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.error.ErrorAttributeOptions.Include;
import org.springframework.boot.webflux.autoconfigure.ReactiveMultipartProperties;
import org.springframework.boot.webflux.autoconfigure.WebFluxProperties;
import org.springframework.boot.webflux.autoconfigure.WebHttpHandlerBuilderCustomizer;
import org.springframework.boot.webflux.error.DefaultErrorAttributes;
import org.springframework.boot.webflux.filter.OrderedHiddenHttpMethodFilter;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.AbstractServerHttpRequest;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.SslInfo;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.adapter.DefaultServerWebExchange;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import org.springframework.web.server.i18n.AcceptHeaderLocaleContextResolver;
import org.springframework.web.server.session.DefaultWebSessionManager;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_webfluxTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test
    void springApplicationDeducesReactiveWebApplicationType() {
        SpringApplication application = new SpringApplication(BasicConfiguration.class);

        assertThat(application.getWebApplicationType()).isEqualTo(WebApplicationType.REACTIVE);
    }

    @Test
    void webFluxPropertiesExposeNestedConfiguration() {
        WebFluxProperties properties = new WebFluxProperties();

        properties.setBasePath("/api");
        properties.setStaticPathPattern("/assets/**");
        properties.setWebjarsPathPattern("/vendor/**");
        properties.getFormat().setDate("yyyy-MM-dd");
        properties.getFormat().setTime("HH:mm:ss");
        properties.getFormat().setDateTime("yyyy-MM-dd'T'HH:mm:ss");
        properties.getProblemdetails().setEnabled(true);
        properties.getApiversion().setRequired(true);
        properties.getApiversion().setDefaultVersion("2026-05-19");
        properties.getApiversion().setSupported(List.of("2026-05-19", "2026-06-01"));
        properties.getApiversion().setDetectSupported(false);
        properties.getApiversion().getUse().setHeader("X-API-Version");
        properties.getApiversion().getUse().setQueryParameter("api-version");
        properties.getApiversion().getUse().setPathSegment(1);
        properties.getApiversion().getUse()
                .setMediaTypeParameter(Map.of(MediaType.APPLICATION_JSON, "version"));

        assertThat(properties.getBasePath()).isEqualTo("/api");
        assertThat(properties.getStaticPathPattern()).isEqualTo("/assets/**");
        assertThat(properties.getWebjarsPathPattern()).isEqualTo("/vendor/**");
        assertThat(properties.getFormat().getDate()).isEqualTo("yyyy-MM-dd");
        assertThat(properties.getFormat().getTime()).isEqualTo("HH:mm:ss");
        assertThat(properties.getFormat().getDateTime()).isEqualTo("yyyy-MM-dd'T'HH:mm:ss");
        assertThat(properties.getProblemdetails().isEnabled()).isTrue();
        assertThat(properties.getApiversion().getRequired()).isTrue();
        assertThat(properties.getApiversion().getDefaultVersion()).isEqualTo("2026-05-19");
        assertThat(properties.getApiversion().getSupported()).containsExactly("2026-05-19", "2026-06-01");
        assertThat(properties.getApiversion().getDetectSupported()).isFalse();
        assertThat(properties.getApiversion().getUse().getHeader()).isEqualTo("X-API-Version");
        assertThat(properties.getApiversion().getUse().getQueryParameter()).isEqualTo("api-version");
        assertThat(properties.getApiversion().getUse().getPathSegment()).isEqualTo(1);
        assertThat(properties.getApiversion().getUse().getMediaTypeParameter())
                .containsEntry(MediaType.APPLICATION_JSON, "version");
    }

    @Test
    void reactiveMultipartPropertiesExposeLimitsAndStorageOptions() {
        ReactiveMultipartProperties properties = new ReactiveMultipartProperties();

        properties.setMaxInMemorySize(DataSize.ofKilobytes(64));
        properties.setMaxHeadersSize(DataSize.ofKilobytes(16));
        properties.setMaxDiskUsagePerPart(DataSize.ofMegabytes(8));
        properties.setMaxParts(12);
        properties.setFileStorageDirectory("/tmp/uploads");
        properties.setHeadersCharset(StandardCharsets.ISO_8859_1);

        assertThat(properties.getMaxInMemorySize()).isEqualTo(DataSize.ofKilobytes(64));
        assertThat(properties.getMaxHeadersSize()).isEqualTo(DataSize.ofKilobytes(16));
        assertThat(properties.getMaxDiskUsagePerPart()).isEqualTo(DataSize.ofMegabytes(8));
        assertThat(properties.getMaxParts()).isEqualTo(12);
        assertThat(properties.getFileStorageDirectory()).isEqualTo("/tmp/uploads");
        assertThat(properties.getHeadersCharset()).isEqualTo(StandardCharsets.ISO_8859_1);
    }

    @Test
    void webHttpHandlerBuilderCustomizerCanAddWebFilter() {
        WebHttpHandlerBuilderCustomizer customizer = (builder) -> builder.filters((filters) ->
                filters.add((exchange, chain) -> {
                    exchange.getResponse().getHeaders().add("X-Customized", "true");
                    return chain.filter(exchange);
                }));
        WebHttpHandlerBuilder builder = WebHttpHandlerBuilder.webHandler((exchange) -> {
            exchange.getResponse().setStatusCode(HttpStatus.ACCEPTED);
            return Mono.empty();
        });
        customizer.customize(builder);
        HttpHandler handler = builder.build();
        TestServerHttpResponse response = new TestServerHttpResponse();

        handler.handle(new TestServerHttpRequest(HttpMethod.GET, URI.create("https://example.test/customizer"),
                new HttpHeaders(), ""), response).block(TIMEOUT);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getHeaders().getFirst("X-Customized")).isEqualTo("true");
    }

    @Test
    void orderedHiddenHttpMethodFilterOverridesFormPostMethod() {
        OrderedHiddenHttpMethodFilter filter = new OrderedHiddenHttpMethodFilter();
        filter.setOrder(123);
        ServerWebExchange exchange = createExchange(HttpMethod.POST, "/items/42", "_method=PATCH&name=alpha");
        CapturingWebFilterChain chain = new CapturingWebFilterChain();

        filter.filter(exchange, chain).block(TIMEOUT);

        assertThat(filter.getOrder()).isEqualTo(123);
        assertThat(chain.getFilteredMethod()).isEqualTo(HttpMethod.PATCH);
    }

    @Test
    void defaultErrorAttributesReturnIncludedDetailsForResponseStatusException() {
        DefaultErrorAttributes attributes = new DefaultErrorAttributes();
        ServerWebExchange exchange = createExchange(HttpMethod.GET, "/catalog/99", "");
        ResponseStatusException failure = new ResponseStatusException(HttpStatus.NOT_FOUND, "missing item");
        attributes.storeErrorInformation(failure, exchange);
        ServerRequest request = createServerRequest(exchange);

        Map<String, Object> result = attributes.getErrorAttributes(request,
                ErrorAttributeOptions.of(Include.STATUS, Include.ERROR, Include.PATH, Include.MESSAGE,
                        Include.EXCEPTION, Include.STACK_TRACE));

        assertThat(attributes.getError(request)).isSameAs(failure);
        assertThat(result).containsEntry("status", 404)
                .containsEntry("error", "Not Found")
                .containsEntry("path", "/catalog/99")
                .containsEntry("message", "missing item")
                .containsEntry("exception", ResponseStatusException.class.getName());
        assertThat(result.get("trace")).asString().contains(ResponseStatusException.class.getName());
        assertThat(result.get("requestId")).isInstanceOf(String.class);
    }

    @Test
    void defaultErrorAttributesUseResponseStatusAnnotationAndKeepFirstStoredError() {
        DefaultErrorAttributes attributes = new DefaultErrorAttributes();
        ServerWebExchange exchange = createExchange(HttpMethod.GET, "/annotated", "");
        AnnotatedBadRequestException failure = new AnnotatedBadRequestException();
        attributes.storeErrorInformation(failure, exchange);
        attributes.storeErrorInformation(new IllegalStateException("second error"), exchange);
        ServerRequest request = createServerRequest(exchange);

        Map<String, Object> result = attributes.getErrorAttributes(request,
                ErrorAttributeOptions.of(Include.STATUS, Include.ERROR, Include.PATH, Include.MESSAGE,
                        Include.EXCEPTION));

        assertThat(attributes.getError(request)).isSameAs(failure);
        assertThat(result).containsEntry("status", 400)
                .containsEntry("error", "Bad Request")
                .containsEntry("path", "/annotated")
                .containsEntry("message", "invalid annotated request")
                .containsEntry("exception", AnnotatedBadRequestException.class.getName());
        assertThat(result).doesNotContainKey("trace");
    }

    @Test
    void defaultErrorAttributesIncludeBindingErrorsWhenRequested() {
        DefaultErrorAttributes attributes = new DefaultErrorAttributes();
        ServerWebExchange exchange = createExchange(HttpMethod.POST, "/registrations", "");
        BindException failure = new BindException(new RegistrationForm(), "registrationForm");
        failure.reject("registration.invalid", "Registration is invalid");
        attributes.storeErrorInformation(failure, exchange);
        ServerRequest request = createServerRequest(exchange);

        Map<String, Object> result = attributes.getErrorAttributes(request,
                ErrorAttributeOptions.of(Include.STATUS, Include.ERROR, Include.PATH, Include.MESSAGE,
                        Include.EXCEPTION, Include.BINDING_ERRORS));

        assertThat(result).containsEntry("status", 500)
                .containsEntry("error", "Internal Server Error")
                .containsEntry("path", "/registrations")
                .containsEntry("message", failure.getMessage())
                .containsEntry("exception", BindException.class.getName());
        assertThat(result.get("errors")).asList()
                .singleElement()
                .satisfies((error) -> assertThat(((MessageSourceResolvable) error).getDefaultMessage())
                        .isEqualTo("Registration is invalid"));
    }

    private static ServerRequest createServerRequest(ServerWebExchange exchange) {
        return ServerRequest.create(exchange, ServerCodecConfigurer.create().getReaders());
    }

    private static ServerWebExchange createExchange(HttpMethod method, String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        if (!body.isEmpty()) {
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setContentLength(body.getBytes(StandardCharsets.UTF_8).length);
        }
        TestServerHttpRequest request = new TestServerHttpRequest(method, URI.create("https://example.test" + path),
                headers, body);
        TestServerHttpResponse response = new TestServerHttpResponse();
        return new DefaultServerWebExchange(request, response, new DefaultWebSessionManager(),
                ServerCodecConfigurer.create(), new AcceptHeaderLocaleContextResolver());
    }

    @Configuration(proxyBeanMethods = false)
    static class BasicConfiguration {

    }

    @ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "invalid annotated request")
    static final class AnnotatedBadRequestException extends RuntimeException {

    }

    static final class RegistrationForm {

    }

    private static final class CapturingWebFilterChain implements WebFilterChain {

        private HttpMethod filteredMethod;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.filteredMethod = exchange.getRequest().getMethod();
            return Mono.empty();
        }

        HttpMethod getFilteredMethod() {
            return this.filteredMethod;
        }

    }

    private static final class TestServerHttpRequest extends AbstractServerHttpRequest {

        private final byte[] body;

        TestServerHttpRequest(HttpMethod method, URI uri, HttpHeaders headers, String body) {
            super(method, uri, "", headers);
            this.body = body.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        protected MultiValueMap<String, HttpCookie> initCookies() {
            return new LinkedMultiValueMap<>();
        }

        @Override
        protected SslInfo initSslInfo() {
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getNativeRequest() {
            return (T) this;
        }

        @Override
        public Flux<DataBuffer> getBody() {
            if (this.body.length == 0) {
                return Flux.empty();
            }
            return Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(this.body));
        }

    }

    private static final class TestServerHttpResponse extends AbstractServerHttpResponse {

        TestServerHttpResponse() {
            super(DefaultDataBufferFactory.sharedInstance);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getNativeResponse() {
            return (T) this;
        }

        @Override
        protected Mono<Void> writeWithInternal(Publisher<? extends DataBuffer> body) {
            return Mono.empty();
        }

        @Override
        protected Mono<Void> writeAndFlushWithInternal(Publisher<? extends Publisher<? extends DataBuffer>> body) {
            return Mono.empty();
        }

        @Override
        protected void applyStatusCode() {
            // No underlying response state is needed for these exchange-level tests.
        }

        @Override
        protected void applyHeaders() {
            // No underlying response state is needed for these exchange-level tests.
        }

        @Override
        protected void applyCookies() {
            // No underlying response state is needed for these exchange-level tests.
        }

    }

}
