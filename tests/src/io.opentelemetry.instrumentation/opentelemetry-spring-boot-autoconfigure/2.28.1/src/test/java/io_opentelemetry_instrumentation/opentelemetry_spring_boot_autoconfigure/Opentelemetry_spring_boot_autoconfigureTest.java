/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_spring_boot_autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

public class Opentelemetry_spring_boot_autoconfigureTest {
    private static final String[] SAFE_OTEL_PROPERTIES = {
        "spring.main.banner-mode=off",
        "otel.service.name=reachability-metadata-test",
        "otel.traces.exporter=none",
        "otel.metrics.exporter=none",
        "otel.logs.exporter=none",
        "otel.propagators=tracecontext,baggage"
    };

    @Test
    void springBootAutoconfigurationProvidesSdkAndWebClientInstrumentation() {
        try (ConfigurableApplicationContext context = newApplicationContext()) {
            OpenTelemetry openTelemetry = context.getBean(OpenTelemetry.class);

            assertConfiguredOpenTelemetrySdk(openTelemetry);
            assertRestTemplateTracePropagation(context.getBean(RestTemplate.class));
        }
    }

    @Test
    void restClientBuilderIsInstrumentedForTracePropagation() {
        try (ConfigurableApplicationContext context = newApplicationContext()) {
            assertRestClientTracePropagation(context.getBean(RestClient.Builder.class));
        }
    }

    @Test
    void sdkDisabledPropertyProvidesNoopOpenTelemetryBean() {
        try (ConfigurableApplicationContext context = newApplicationContext("otel.sdk.disabled=true")) {
            OpenTelemetry openTelemetry = context.getBean(OpenTelemetry.class);
            Span span = openTelemetry.getTracer("reachability-metadata-disabled-test")
                    .spanBuilder("disabled-sdk-span")
                    .startSpan();

            try {
                assertThat(span.getSpanContext().isValid()).isFalse();

                Map<String, String> carrier = new HashMap<>();
                try (Scope ignored = span.makeCurrent()) {
                    openTelemetry.getPropagators()
                            .getTextMapPropagator()
                            .inject(Context.current(), carrier, Map::put);
                }

                assertThat(carrier).isEmpty();
            } finally {
                span.end();
            }
        }
    }

    private static void assertConfiguredOpenTelemetrySdk(OpenTelemetry openTelemetry) {
        assertThat(openTelemetry).isInstanceOf(ExtendedOpenTelemetry.class);

        Tracer tracer = openTelemetry.getTracer("reachability-metadata-test");
        Span span = tracer.spanBuilder("autoconfigured-sdk-span").startSpan();
        SpanContext spanContext = span.getSpanContext();
        assertThat(spanContext.isValid()).isTrue();

        Map<String, String> carrier = new HashMap<>();
        try (Scope ignored = span.makeCurrent()) {
            TextMapPropagator propagator = openTelemetry.getPropagators().getTextMapPropagator();
            propagator.inject(Context.current(), carrier, Map::put);
        } finally {
            span.end();
        }

        assertThat(carrier).containsKey("traceparent");
        assertThat(carrier.get("traceparent")).startsWith("00-" + spanContext.getTraceId());

        SpanContext extractedContext = Span.fromContext(openTelemetry.getPropagators()
                        .getTextMapPropagator()
                        .extract(Context.root(), carrier, MapTextGetter.INSTANCE))
                .getSpanContext();
        assertThat(extractedContext.isValid()).isTrue();
        assertThat(extractedContext.isRemote()).isTrue();
        assertThat(extractedContext.getTraceId()).isEqualTo(spanContext.getTraceId());
    }

    private static void assertRestTemplateTracePropagation(RestTemplate restTemplate) {
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        server.expect(once(), requestTo("https://example.test/messages"))
                .andExpect(header("traceparent", startsWith("00-")))
                .andRespond(withSuccess("instrumented", MediaType.TEXT_PLAIN));

        String body = restTemplate.getForObject("https://example.test/messages", String.class);

        assertThat(body).isEqualTo("instrumented");
        server.verify(Duration.ofSeconds(10));
    }

    private static void assertRestClientTracePropagation(RestClient.Builder builder) {
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        server.expect(once(), requestTo("https://example.test/rest-client"))
                .andExpect(header("traceparent", startsWith("00-")))
                .andRespond(withSuccess("rest-client-instrumented", MediaType.TEXT_PLAIN));

        String body = restClient.get()
                .uri("https://example.test/rest-client")
                .retrieve()
                .body(String.class);

        assertThat(body).isEqualTo("rest-client-instrumented");
        server.verify(Duration.ofSeconds(10));
    }

    private static ConfigurableApplicationContext newApplicationContext(String... additionalProperties) {
        String[] properties = Arrays.copyOf(
                SAFE_OTEL_PROPERTIES,
                SAFE_OTEL_PROPERTIES.length + additionalProperties.length);
        System.arraycopy(
                additionalProperties,
                0,
                properties,
                SAFE_OTEL_PROPERTIES.length,
                additionalProperties.length);

        return new SpringApplicationBuilder(TestApplication.class)
                .web(WebApplicationType.NONE)
                .properties(properties)
                .run();
    }

    @SpringBootApplication
    static class TestApplication {
        @Bean
        RestTemplate restTemplate(RestTemplateBuilder builder) {
            return builder.connectTimeout(Duration.ofSeconds(10))
                    .readTimeout(Duration.ofSeconds(10))
                    .build();
        }
    }

    private enum MapTextGetter implements TextMapGetter<Map<String, String>> {
        INSTANCE;

        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier.get(key);
        }
    }
}
