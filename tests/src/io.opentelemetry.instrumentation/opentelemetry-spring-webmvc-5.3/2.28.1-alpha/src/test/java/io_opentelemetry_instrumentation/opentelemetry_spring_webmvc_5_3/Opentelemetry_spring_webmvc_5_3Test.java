/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_spring_webmvc_5_3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.spring.webmvc.v5_3.SpringWebMvcTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;

public class Opentelemetry_spring_webmvc_5_3Test {
    private static final AttributeKey<String> REQUEST_URI =
            AttributeKey.stringKey("test.request_uri");
    private static final AttributeKey<Long> RESPONSE_STATUS =
            AttributeKey.longKey("test.response_status");

    @Test
    void builderCreatedServletFilterStartsServerSpanAroundSpringWebMvcRequest() throws Exception {
        try (TelemetryFixture fixture = TelemetryFixture.create()) {
            AtomicBoolean chainInvoked = new AtomicBoolean(false);
            SpringWebMvcTelemetry telemetry = SpringWebMvcTelemetry.builder(fixture.openTelemetry())
                    .setKnownMethods(Arrays.asList("GET", "POST"))
                    .setCapturedRequestHeaders(List.of("X-Test-Request"))
                    .setCapturedResponseHeaders(List.of("X-Test-Response"))
                    .setSpanNameExtractorCustomizer(
                            original -> request -> "custom " + request.getMethod())
                    .addAttributesExtractor(new RequestResponseAttributesExtractor())
                    .build();
            Filter filter = telemetry.createServletFilter();
            init(filter);

            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/shop/orders/42");
            request.setContextPath("/shop");
            request.setServletPath("/orders/42");
            request.setScheme("https");
            request.setServerName("example.test");
            request.setServerPort(443);
            request.addHeader("X-Test-Request", "created-by-test");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, (chainRequest, chainResponse) -> {
                chainInvoked.set(true);
                assertThat(chainRequest).isInstanceOf(HttpServletRequest.class);
                assertThat(chainRequest).isNotSameAs(request);
                assertThat(chainResponse).isSameAs(response);
                assertThat(Span.current().getSpanContext().isValid()).isTrue();
                ((HttpServletResponse) chainResponse).setStatus(HttpServletResponse.SC_ACCEPTED);
                ((HttpServletResponse) chainResponse).addHeader("X-Test-Response", "accepted");
            });

            assertThat(chainInvoked).isTrue();
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_ACCEPTED);
            assertThat(response.getHeader("X-Test-Response")).isEqualTo("accepted");
            SpanData span = onlyFinishedSpan(fixture);
            assertThat(span.getName()).isEqualTo("custom POST");
            assertThat(span.getKind()).isEqualTo(SpanKind.SERVER);
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
            assertThat(span.getAttributes().get(REQUEST_URI)).isEqualTo("/shop/orders/42");
            assertThat(span.getAttributes().get(RESPONSE_STATUS)).isEqualTo(202L);
        }
    }

    @Test
    void createServletFilterReportsThrownExceptionAndRethrowsIt() throws Exception {
        try (TelemetryFixture fixture = TelemetryFixture.create()) {
            Filter filter = SpringWebMvcTelemetry.create(fixture.openTelemetry())
                    .createServletFilter();
            init(filter);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/failure");
            request.setServerName("example.test");
            MockHttpServletResponse response = new MockHttpServletResponse();
            IllegalStateException failure = new IllegalStateException("controller failed");

            assertThatThrownBy(() -> filter.doFilter(request, response, throwingChain(failure)))
                    .isSameAs(failure);

            SpanData span = onlyFinishedSpan(fixture);
            assertThat(span.getKind()).isEqualTo(SpanKind.SERVER);
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            assertThat(span.getEvents())
                    .anySatisfy(event -> assertThat(event.getName()).isEqualTo("exception"));
        }
    }

    @Test
    void createServletFilterReturnsOrderedFilterWithNoopOpenTelemetry() throws Exception {
        Filter filter = SpringWebMvcTelemetry.create(OpenTelemetry.noop()).createServletFilter();
        init(filter);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/noop");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (chainRequest, chainResponse) -> chainInvoked.set(true));

        assertThat(chainInvoked).isTrue();
        assertThat(filter).isInstanceOf(Ordered.class);
        assertThat(((Ordered) filter).getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1);
    }

    private static void init(Filter filter) throws ServletException {
        filter.init(new MockFilterConfig(new MockServletContext(), "otelSpringWebMvc"));
    }

    private static FilterChain throwingChain(RuntimeException failure) {
        return new FilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response)
                    throws IOException, ServletException {
                throw failure;
            }
        };
    }

    private static SpanData onlyFinishedSpan(TelemetryFixture fixture) {
        List<SpanData> spans = fixture.spanExporter().getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        return spans.get(0);
    }

    private static final class RequestResponseAttributesExtractor
            implements AttributesExtractor<HttpServletRequest, HttpServletResponse> {
        @Override
        public void onStart(
                AttributesBuilder attributes, Context parentContext, HttpServletRequest request) {
            attributes.put(REQUEST_URI, request.getRequestURI());
        }

        @Override
        public void onEnd(
                AttributesBuilder attributes,
                Context context,
                HttpServletRequest request,
                HttpServletResponse response,
                Throwable error) {
            if (response != null) {
                attributes.put(RESPONSE_STATUS, (long) response.getStatus());
            }
        }
    }

    private static final class TelemetryFixture implements AutoCloseable {
        private final InMemorySpanExporter spanExporter;
        private final OpenTelemetrySdk openTelemetry;

        private TelemetryFixture(
                InMemorySpanExporter spanExporter, OpenTelemetrySdk openTelemetry) {
            this.spanExporter = spanExporter;
            this.openTelemetry = openTelemetry;
        }

        static TelemetryFixture create() {
            InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
            SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                    .build();
            OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .build();
            return new TelemetryFixture(spanExporter, openTelemetry);
        }

        OpenTelemetry openTelemetry() {
            return openTelemetry;
        }

        InMemorySpanExporter spanExporter() {
            return spanExporter;
        }

        @Override
        public void close() {
            openTelemetry.shutdown().join(10, TimeUnit.SECONDS);
        }
    }
}
