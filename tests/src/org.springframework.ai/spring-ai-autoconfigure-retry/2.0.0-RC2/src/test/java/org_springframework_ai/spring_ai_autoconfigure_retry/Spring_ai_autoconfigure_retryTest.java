/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_autoconfigure_retry;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryProperties;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Spring_ai_autoconfigure_retryTest {

    @Test
    void autoConfigurationCreatesRetryInfrastructureWithDefaultProperties() throws Exception {
        try (ConfigurableApplicationContext context = applicationContext(Map.of())) {
            SpringAiRetryProperties properties = context.getBean(SpringAiRetryProperties.class);
            RetryTemplate retryTemplate = context.getBean(RetryTemplate.class);
            ResponseErrorHandler responseErrorHandler = context.getBean(ResponseErrorHandler.class);

            assertThat(properties.getMaxAttempts()).isEqualTo(10);
            assertThat(properties.getBackoff().getInitialInterval()).isEqualTo(Duration.ofSeconds(2));
            assertThat(properties.getBackoff().getMultiplier()).isEqualTo(5);
            assertThat(properties.getBackoff().getMaxInterval()).isEqualTo(Duration.ofMinutes(3));
            assertThat(properties.isOnClientErrors()).isFalse();
            assertThat(properties.getOnHttpCodes()).isEmpty();
            assertThat(properties.getExcludeOnHttpCodes()).isEmpty();
            assertThat(retryTemplate.getRetryPolicy()).isNotNull();
            assertThat(retryTemplate.getRetryListener()).isNotNull();
            assertThat(responseErrorHandler.hasError(response(HttpStatus.OK, "ok"))).isFalse();
            assertThat(responseErrorHandler.hasError(response(HttpStatus.INTERNAL_SERVER_ERROR, "error"))).isTrue();
        }
    }

    @Test
    void propertyBindingConfiguresRetryTemplateAndResponseErrorHandler() throws Exception {
        Map<String, Object> properties = Map.of(
                "spring.ai.retry.max-attempts", "2",
                "spring.ai.retry.backoff.initial-interval", "1ms",
                "spring.ai.retry.backoff.multiplier", "1",
                "spring.ai.retry.backoff.max-interval", "1ms",
                "spring.ai.retry.on-client-errors", "true",
                "spring.ai.retry.on-http-codes", "429,418",
                "spring.ai.retry.exclude-on-http-codes", "503");

        try (ConfigurableApplicationContext context = applicationContext(properties)) {
            SpringAiRetryProperties boundProperties = context.getBean(SpringAiRetryProperties.class);
            RetryTemplate retryTemplate = context.getBean(RetryTemplate.class);
            ResponseErrorHandler responseErrorHandler = context.getBean(ResponseErrorHandler.class);

            assertThat(boundProperties.getMaxAttempts()).isEqualTo(2);
            assertThat(boundProperties.getBackoff().getInitialInterval()).isEqualTo(Duration.ofMillis(1));
            assertThat(boundProperties.getBackoff().getMultiplier()).isEqualTo(1);
            assertThat(boundProperties.getBackoff().getMaxInterval()).isEqualTo(Duration.ofMillis(1));
            assertThat(boundProperties.isOnClientErrors()).isTrue();
            assertThat(boundProperties.getOnHttpCodes()).containsExactly(429, 418);
            assertThat(boundProperties.getExcludeOnHttpCodes()).containsExactly(503);

            AtomicInteger transientAttempts = new AtomicInteger();
            String result = retryTemplate.execute(() -> {
                if (transientAttempts.incrementAndGet() == 1) {
                    throw new TransientAiException("retry this failure");
                }
                return "recovered";
            });
            assertThat(result).isEqualTo("recovered");
            assertThat(transientAttempts.get()).isEqualTo(2);

            AtomicInteger nonTransientAttempts = new AtomicInteger();
            assertThatThrownBy(() -> retryTemplate.execute(() -> {
                nonTransientAttempts.incrementAndGet();
                throw new NonTransientAiException("do not retry this failure");
            })).isInstanceOf(RetryException.class);
            assertThat(nonTransientAttempts.get()).isEqualTo(1);

            assertThatThrownBy(() -> handle(responseErrorHandler, HttpStatus.TOO_MANY_REQUESTS, "rate limited"))
                    .isInstanceOf(TransientAiException.class)
                    .hasMessageContaining("HTTP 429")
                    .hasMessageContaining("rate limited");
            assertThatThrownBy(() -> handle(responseErrorHandler, HttpStatus.SERVICE_UNAVAILABLE, "maintenance"))
                    .isInstanceOf(NonTransientAiException.class)
                    .hasMessageContaining("HTTP 503")
                    .hasMessageContaining("maintenance");
            assertThatThrownBy(() -> handle(responseErrorHandler, HttpStatus.NOT_FOUND, "missing"))
                    .isInstanceOf(TransientAiException.class)
                    .hasMessageContaining("HTTP 404")
                    .hasMessageContaining("missing");
        }
    }

    @Test
    void defaultResponseErrorHandlerDoesNotRetryClientErrorsUnlessConfigured() throws Exception {
        try (ConfigurableApplicationContext context = applicationContext(Map.of())) {
            ResponseErrorHandler responseErrorHandler = context.getBean(ResponseErrorHandler.class);

            assertThatThrownBy(() -> handle(responseErrorHandler, HttpStatus.BAD_REQUEST, "invalid request"))
                    .isInstanceOf(NonTransientAiException.class)
                    .hasMessageContaining("HTTP 400")
                    .hasMessageContaining("invalid request");
            assertThatThrownBy(() -> handle(responseErrorHandler, HttpStatus.BAD_GATEWAY, "upstream failed"))
                    .isInstanceOf(TransientAiException.class)
                    .hasMessageContaining("HTTP 502")
                    .hasMessageContaining("upstream failed");
        }
    }

    @Test
    void autoConfigurationBacksOffWhenApplicationProvidesRetryBeans() throws Exception {
        try (ConfigurableApplicationContext context = applicationContext(Map.of(), CustomRetryConfiguration.class)) {
            RetryTemplate retryTemplate = context.getBean(RetryTemplate.class);
            ResponseErrorHandler responseErrorHandler = context.getBean(ResponseErrorHandler.class);

            assertThat(retryTemplate).isSameAs(context.getBean("customRetryTemplate"));
            assertThat(responseErrorHandler).isSameAs(context.getBean("customResponseErrorHandler"));
            assertThat(responseErrorHandler.hasError(response(HttpStatus.INTERNAL_SERVER_ERROR, "ignored"))).isFalse();
        }
    }

    private static ConfigurableApplicationContext applicationContext(Map<String, Object> properties,
            Class<?>... extraSources) {
        Class<?>[] sources = new Class<?>[extraSources.length + 1];
        System.arraycopy(extraSources, 0, sources, 0, extraSources.length);
        sources[extraSources.length] = SpringAiRetryAutoConfiguration.class;
        return new SpringApplicationBuilder(sources)
                .web(WebApplicationType.NONE)
                .properties(properties)
                .logStartupInfo(false)
                .registerShutdownHook(false)
                .run();
    }

    private static void handle(ResponseErrorHandler responseErrorHandler, HttpStatus status, String body)
            throws IOException {
        ClientHttpResponse response = response(status, body);
        responseErrorHandler.handleError(URI.create("https://example.test/retry"), HttpMethod.GET, response);
    }

    private static ClientHttpResponse response(HttpStatusCode status, String body) {
        return new StaticClientHttpResponse(status, body);
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomRetryConfiguration {

        @Bean
        RetryTemplate customRetryTemplate() {
            return new RetryTemplate(RetryPolicy.withMaxRetries(0));
        }

        @Bean
        ResponseErrorHandler customResponseErrorHandler() {
            return response -> false;
        }
    }

    private static final class StaticClientHttpResponse implements ClientHttpResponse {

        private final HttpStatusCode status;

        private final byte[] body;

        private StaticClientHttpResponse(HttpStatusCode status, String body) {
            this.status = status;
            this.body = body.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public HttpStatusCode getStatusCode() {
            return this.status;
        }

        @Override
        public String getStatusText() {
            return this.status.toString();
        }

        @Override
        public HttpHeaders getHeaders() {
            return new HttpHeaders();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(Arrays.copyOf(this.body, this.body.length));
        }

        @Override
        public void close() {
        }
    }
}
