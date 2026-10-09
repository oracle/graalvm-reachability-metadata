/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_autoconfigure_retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.ResponseErrorHandler;

public class Spring_ai_autoconfigure_retryTest {

    @Test
    void autoConfigurationIsRegisteredForSpringBootDiscovery() {
        ClassLoader classLoader = Spring_ai_autoconfigure_retryTest.class.getClassLoader();
        assertThat(ImportCandidates.load(AutoConfiguration.class, classLoader).getCandidates())
                .contains(SpringAiRetryAutoConfiguration.class.getName());
    }

    @Test
    void autoConfigurationCreatesRetryBeansWithDocumentedDefaults() throws IOException {
        try (AnnotationConfigApplicationContext context = contextWithProperties(Map.of())) {
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
            assertThat(retryTemplate.getRetryPolicy().shouldRetry(new TransientAiException("temporary"))).isTrue();
            assertThat(responseErrorHandler.hasError(response(200, "ok"))).isFalse();
        }
    }

    @Test
    void configurationPropertiesControlRetryPolicyAndBackoff() throws Exception {
        Map<String, Object> environment = Map.of(
                "spring.ai.retry.max-attempts", "3",
                "spring.ai.retry.backoff.initial-interval", "5ms",
                "spring.ai.retry.backoff.multiplier", "2",
                "spring.ai.retry.backoff.max-interval", "12ms");

        try (AnnotationConfigApplicationContext context = contextWithProperties(environment)) {
            SpringAiRetryProperties properties = context.getBean(SpringAiRetryProperties.class);
            RetryTemplate retryTemplate = context.getBean(RetryTemplate.class);

            assertThat(properties.getMaxAttempts()).isEqualTo(3);
            assertThat(properties.getBackoff().getInitialInterval()).isEqualTo(Duration.ofMillis(5));
            assertThat(properties.getBackoff().getMultiplier()).isEqualTo(2);
            assertThat(properties.getBackoff().getMaxInterval()).isEqualTo(Duration.ofMillis(12));

            var backOff = retryTemplate.getRetryPolicy().getBackOff().start();
            assertThat(backOff.nextBackOff()).isEqualTo(5);
            assertThat(backOff.nextBackOff()).isEqualTo(10);
            assertThat(backOff.nextBackOff()).isEqualTo(12);
            assertThat(retryTemplate.getRetryPolicy().shouldRetry(new ResourceAccessException("temporary"))).isTrue();
            assertThat(retryTemplate.getRetryPolicy().shouldRetry(new NonTransientAiException("permanent"))).isFalse();

            AtomicInteger attempts = new AtomicInteger();
            String result = retryTemplate.execute(() -> {
                if (attempts.incrementAndGet() < 3) {
                    throw new TransientAiException("temporary");
                }
                return "success";
            });

            assertThat(result).isEqualTo("success");
            assertThat(attempts).hasValue(3);
        }
    }

    @Test
    void retryTemplateRetriesResourceAccessFailures() throws RetryException {
        try (AnnotationConfigApplicationContext context = contextWithProperties(Map.of(
                "spring.ai.retry.max-attempts", "2",
                "spring.ai.retry.backoff.initial-interval", "0ms"))) {
            RetryTemplate retryTemplate = context.getBean(RetryTemplate.class);
            AtomicInteger attempts = new AtomicInteger();

            String result = retryTemplate.execute(() -> {
                if (attempts.getAndIncrement() == 0) {
                    throw new ResourceAccessException("temporary connection failure");
                }
                return "success";
            });

            assertThat(result).isEqualTo("success");
            assertThat(attempts).hasValue(2);
        }
    }

    @Test
    void retryTemplateStopsForNonTransientFailures() {
        try (AnnotationConfigApplicationContext context = contextWithProperties(Map.of(
                "spring.ai.retry.backoff.initial-interval", "0ms"))) {
            RetryTemplate retryTemplate = context.getBean(RetryTemplate.class);
            AtomicInteger attempts = new AtomicInteger();

            assertThatThrownBy(() -> retryTemplate.execute(() -> {
                attempts.incrementAndGet();
                throw new NonTransientAiException("permanent");
            })).isInstanceOf(RetryException.class)
                    .hasCauseInstanceOf(NonTransientAiException.class);
            assertThat(attempts).hasValue(1);
        }
    }

    @Test
    void responseErrorHandlerClassifiesDefaultHttpErrors() throws IOException {
        try (AnnotationConfigApplicationContext context = contextWithProperties(Map.of())) {
            ResponseErrorHandler handler = context.getBean(ResponseErrorHandler.class);

            handler.handleError(URI.create("https://example.test"), HttpMethod.GET, response(204, ""));
            assertThat(handler.hasError(response(204, ""))).isFalse();
            assertThat(handler.hasError(response(404, "missing"))).isTrue();

            assertThatThrownBy(() -> handler.handleError(URI.create("https://example.test"), HttpMethod.GET,
                    response(404, "missing"))).isInstanceOf(NonTransientAiException.class)
                    .hasMessage("HTTP 404 - missing");
            assertThatThrownBy(() -> handler.handleError(URI.create("https://example.test"), HttpMethod.GET,
                    response(503, ""))).isInstanceOf(TransientAiException.class)
                    .hasMessage("HTTP 503 - No response body available");
        }
    }

    @Test
    void responseErrorHandlerHonorsTransientAndExcludedHttpCodes() throws IOException {
        Map<String, Object> environment = Map.of(
                "spring.ai.retry.on-client-errors", "true",
                "spring.ai.retry.on-http-codes[0]", "429",
                "spring.ai.retry.exclude-on-http-codes[0]", "503");

        try (AnnotationConfigApplicationContext context = contextWithProperties(environment)) {
            SpringAiRetryProperties properties = context.getBean(SpringAiRetryProperties.class);
            ResponseErrorHandler handler = context.getBean(ResponseErrorHandler.class);

            assertThat(properties.isOnClientErrors()).isTrue();
            assertThat(properties.getOnHttpCodes()).containsExactly(429);
            assertThat(properties.getExcludeOnHttpCodes()).containsExactly(503);
            assertThatThrownBy(() -> handler.handleError(URI.create("https://example.test"), HttpMethod.POST,
                    response(404, "client failure"))).isInstanceOf(TransientAiException.class);
            assertThatThrownBy(() -> handler.handleError(URI.create("https://example.test"), HttpMethod.POST,
                    response(429, "rate limited"))).isInstanceOf(TransientAiException.class);
            assertThatThrownBy(() -> handler.handleError(URI.create("https://example.test"), HttpMethod.POST,
                    response(503, "service unavailable"))).isInstanceOf(NonTransientAiException.class);
        }
    }

    @Test
    void autoConfigurationBacksOffForUserSuppliedRetryBeans() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(UserBeansConfiguration.class, SpringAiRetryAutoConfiguration.class);
            context.refresh();

            assertThat(context.getBean(RetryTemplate.class))
                    .isSameAs(context.getBean("userRetryTemplate", RetryTemplate.class));
            assertThat(context.getBean(ResponseErrorHandler.class))
                    .isSameAs(context.getBean("userResponseErrorHandler", ResponseErrorHandler.class));
        }
    }

    private static AnnotationConfigApplicationContext contextWithProperties(Map<String, Object> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("test-properties", properties));
        context.register(SpringAiRetryAutoConfiguration.class);
        context.refresh();
        return context;
    }

    private static ClientHttpResponse response(int status, String body) {
        return new ClientHttpResponse() {

            @Override
            public HttpStatusCode getStatusCode() {
                return HttpStatusCode.valueOf(status);
            }

            @Override
            public String getStatusText() {
                return "";
            }

            @Override
            public HttpHeaders getHeaders() {
                return new HttpHeaders();
            }

            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public void close() {
            }

        };
    }

    @Configuration(proxyBeanMethods = false)
    static class UserBeansConfiguration {

        @Bean
        RetryTemplate userRetryTemplate() {
            return new RetryTemplate();
        }

        @Bean
        ResponseErrorHandler userResponseErrorHandler() {
            return response -> false;
        }

    }

}
