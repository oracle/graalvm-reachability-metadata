/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_exporter_otlp_common;

import static io.opentelemetry.api.common.AttributeKey.booleanArrayKey;
import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.doubleArrayKey;
import static io.opentelemetry.api.common.AttributeKey.doubleKey;
import static io.opentelemetry.api.common.AttributeKey.longArrayKey;
import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.internal.marshal.Marshaler;
import io.opentelemetry.exporter.internal.otlp.InstrumentationScopeMarshaler;
import io.opentelemetry.exporter.internal.otlp.KeyValueMarshaler;
import io.opentelemetry.exporter.internal.otlp.ResourceMarshaler;
import io.opentelemetry.exporter.internal.otlp.StringAnyValueMarshaler;
import io.opentelemetry.exporter.internal.otlp.logs.LogsRequestMarshaler;
import io.opentelemetry.exporter.internal.otlp.metrics.MetricsRequestMarshaler;
import io.opentelemetry.exporter.internal.otlp.traces.TraceRequestMarshaler;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class Opentelemetry_exporter_otlp_commonTest {
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID = "00f067aa0ba902b7";
    private static final String LINK_SPAN_ID = "1111111111111111";

    @Test
    void commonMarshalersSerializeResourcesScopesAndAllAttributeValueKinds() throws IOException {
        Attributes attributes = Attributes.builder()
                .put(stringKey("service.name"), "checkout")
                .put(longKey("process.pid"), 4242L)
                .put(doubleKey("cpu.load"), 0.75)
                .put(booleanKey("healthy"), true)
                .put(stringArrayKey("roles"), Arrays.asList("frontend", "payments"))
                .put(longArrayKey("ports"), Arrays.asList(8080L, 8443L))
                .put(doubleArrayKey("latency.bounds"), Arrays.asList(1.5, 9.5))
                .put(booleanArrayKey("feature.flags"), Arrays.asList(true, false))
                .build();

        KeyValueMarshaler[] keyValueMarshalers = KeyValueMarshaler.createRepeated(attributes);

        assertThat(keyValueMarshalers).hasSize(8);
        assertThat(jsonOf(keyValueMarshalers)).contains(
                "\"key\":\"service.name\"",
                "\"stringValue\":\"checkout\"",
                "\"key\":\"process.pid\"",
                "\"intValue\":\"4242\"",
                "\"doubleValue\":0.75",
                "\"boolValue\":true",
                "\"arrayValue\"",
                "\"frontend\"",
                "\"8443\"",
                "\"feature.flags\"");

        ResourceMarshaler resourceMarshaler = ResourceMarshaler.create(Resource.create(attributes));
        assertBinarySizeMatches(resourceMarshaler);
        assertThat(jsonOf(resourceMarshaler)).contains(
                "\"attributes\"",
                "\"key\":\"service.name\"",
                "\"stringValue\":\"checkout\"");

        InstrumentationScopeInfo scopeInfo = InstrumentationScopeInfo.builder("scope.common")
                .setVersion("1.0.0")
                .setSchemaUrl("https://opentelemetry.io/schemas/1.9.0")
                .build();
        InstrumentationScopeMarshaler scopeMarshaler =
                InstrumentationScopeMarshaler.create(scopeInfo);
        assertBinarySizeMatches(scopeMarshaler);
        assertThat(jsonOf(scopeMarshaler)).contains(
                "\"name\":\"scope.common\"",
                "\"version\":\"1.0.0\"");

        StringAnyValueMarshaler bodyMarshaler =
                new StringAnyValueMarshaler(bytes("standalone body"));
        assertBinarySizeMatches(bodyMarshaler);
        assertThat(jsonOf(bodyMarshaler)).contains("\"stringValue\":\"standalone body\"");
    }

    @Test
    void traceRequestMarshalsEndedSpansWithEventsLinksStatusAndResource() throws IOException {
        InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(testResource())
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        try {
            SpanContext parentContext = spanContext(SPAN_ID);
            SpanContext linkContext = spanContext(LINK_SPAN_ID);
            Span span = tracerProvider.tracerBuilder("trace.scope")
                    .setInstrumentationVersion("1.2.3")
                    .setSchemaUrl("https://opentelemetry.io/schemas/1.9.0")
                    .build()
                    .spanBuilder("GET /checkout")
                    .setSpanKind(SpanKind.SERVER)
                    .setParent(Context.root().with(Span.wrap(parentContext)))
                    .setAttribute("http.method", "GET")
                    .setAttribute("http.status_code", 503L)
                    .addLink(linkContext, Attributes.of(stringKey("link.type"), "retry"))
                    .startSpan();
            try (Scope scope = span.makeCurrent()) {
                span.addEvent("cache.miss", Attributes.of(booleanKey("cold"), true));
                span.setStatus(StatusCode.ERROR, "downstream refused");
            } finally {
                span.end();
            }

            List<SpanData> spans = spanExporter.getFinishedSpanItems();
            assertThat(spans).hasSize(1);

            TraceRequestMarshaler marshaler = TraceRequestMarshaler.create(spans);
            assertBinarySizeMatches(marshaler);
            assertThat(jsonOf(marshaler)).contains(
                    "\"resourceSpans\"",
                    "\"scopeSpans\"",
                    "\"spans\"",
                    "\"traceId\":\"" + TRACE_ID + "\"",
                    "\"parentSpanId\":\"" + SPAN_ID + "\"",
                    "\"name\":\"GET /checkout\"",
                    "\"kind\":\"SPAN_KIND_SERVER\"",
                    "\"events\"",
                    "\"cache.miss\"",
                    "\"links\"",
                    "\"retry\"",
                    "\"status\"",
                    "\"downstream refused\"");
        } finally {
            tracerProvider.close();
        }
    }

    @Test
    void metricsRequestMarshalsCounterGaugeAndHistogramCollections() throws IOException {
        InMemoryMetricReader metricReader = InMemoryMetricReader.create();
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .setResource(testResource())
                .registerMetricReader(metricReader)
                .build();
        ObservableDoubleGauge gauge = meterProvider.meterBuilder("metrics.scope")
                .setInstrumentationVersion("2.0.0")
                .build()
                .gaugeBuilder("queue.depth")
                .setDescription("Queued checkout jobs")
                .setUnit("jobs")
                .buildWithCallback(measurement -> measurement.record(
                        5.5,
                        Attributes.of(stringKey("queue"), "priority")));
        try {
            meterProvider.get("metrics.scope")
                    .counterBuilder("orders.created")
                    .setDescription("Created orders")
                    .setUnit("1")
                    .build()
                    .add(7, Attributes.of(stringKey("region"), "west"));
            meterProvider.get("metrics.scope")
                    .histogramBuilder("checkout.latency")
                    .setDescription("Checkout latency")
                    .setUnit("ms")
                    .build()
                    .record(17.5, Attributes.of(stringKey("route"), "/checkout"));

            Collection<MetricData> metrics = metricReader.collectAllMetrics();
            assertThat(metrics.size()).isGreaterThanOrEqualTo(3);

            MetricsRequestMarshaler marshaler = MetricsRequestMarshaler.create(metrics);
            assertBinarySizeMatches(marshaler);
            assertThat(jsonOf(marshaler)).contains(
                    "\"resourceMetrics\"",
                    "\"scopeMetrics\"",
                    "\"metrics\"",
                    "\"name\":\"orders.created\"",
                    "\"sum\"",
                    "\"asInt\":\"7\"",
                    "\"name\":\"checkout.latency\"",
                    "\"histogram\"",
                    "\"count\":\"1\"",
                    "\"sum\":17.5",
                    "\"name\":\"queue.depth\"",
                    "\"gauge\"",
                    "\"asDouble\":5.5");
        } finally {
            gauge.close();
            meterProvider.close();
        }
    }

    @Test
    void metricsRequestMarshalsNonMonotonicUpDownCounterSums() throws IOException {
        InMemoryMetricReader metricReader = InMemoryMetricReader.create();
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .setResource(testResource())
                .registerMetricReader(metricReader)
                .build();
        try {
            LongUpDownCounter activeSessions = meterProvider.get("updown.metrics.scope")
                    .upDownCounterBuilder("sessions.active")
                    .setDescription("Currently active sessions")
                    .setUnit("sessions")
                    .build();
            Attributes attributes = Attributes.of(stringKey("state"), "connected");
            activeSessions.add(3, attributes);
            activeSessions.add(-1, attributes);

            Collection<MetricData> metrics = metricReader.collectAllMetrics();
            assertThat(metrics)
                    .filteredOn(metric -> metric.getName().equals("sessions.active"))
                    .singleElement()
                    .satisfies(metric -> {
                        assertThat(metric.getType().name()).isEqualTo("LONG_SUM");
                        assertThat(metric.getLongSumData().isMonotonic()).isFalse();
                    });

            MetricsRequestMarshaler marshaler = MetricsRequestMarshaler.create(metrics);
            assertBinarySizeMatches(marshaler);
            assertThat(jsonOf(marshaler))
                    .contains(
                            "\"name\":\"sessions.active\"",
                            "\"sum\"",
                            "\"aggregationTemporality\"",
                            "\"asInt\":\"2\"",
                            "\"key\":\"state\"",
                            "\"stringValue\":\"connected\"")
                    .doesNotContain("\"isMonotonic\":true");
        } finally {
            meterProvider.close();
        }
    }

    @Test
    void metricsRequestMarshalsHistogramExemplarsWithTraceContext() throws IOException {
        InMemoryMetricReader metricReader = InMemoryMetricReader.create();
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .setResource(testResource())
                .registerMetricReader(metricReader)
                .build();
        try {
            try (Scope scope = Context.root().with(Span.wrap(spanContext(SPAN_ID))).makeCurrent()) {
                meterProvider.get("exemplar.scope")
                        .histogramBuilder("request.duration")
                        .setDescription("Request duration")
                        .setUnit("ms")
                        .build()
                        .record(42.5, Attributes.of(stringKey("route"), "/cart"));
            }

            Collection<MetricData> metrics = metricReader.collectAllMetrics();
            assertThat(metrics)
                    .filteredOn(metric -> metric.getName().equals("request.duration"))
                    .singleElement()
                    .satisfies(metric -> assertThat(metric.getHistogramData().getPoints())
                            .singleElement()
                            .satisfies(point -> assertThat(point.getExemplars()).hasSize(1)));

            MetricsRequestMarshaler marshaler = MetricsRequestMarshaler.create(metrics);
            assertBinarySizeMatches(marshaler);
            assertThat(jsonOf(marshaler)).contains(
                    "\"name\":\"request.duration\"",
                    "\"histogram\"",
                    "\"exemplars\"",
                    "\"asDouble\":42.5",
                    "\"traceId\":\"" + TRACE_ID + "\"",
                    "\"spanId\":\"" + SPAN_ID + "\"");
        } finally {
            meterProvider.close();
        }
    }

    @Test
    void logsRequestMarshalsBodySeverityAttributesAndTraceContext() throws IOException {
        InMemoryLogRecordExporter logExporter = InMemoryLogRecordExporter.create();
        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .setResource(testResource())
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(logExporter))
                .build();
        try {
            Logger logger = loggerProvider.loggerBuilder("logs.scope")
                    .setInstrumentationVersion("3.1.4")
                    .setSchemaUrl("https://opentelemetry.io/schemas/1.9.0")
                    .build();
            logger.logRecordBuilder()
                    .setEpoch(1234567890L, TimeUnit.NANOSECONDS)
                    .setContext(Context.root().with(Span.wrap(spanContext(SPAN_ID))))
                    .setSeverity(Severity.WARN)
                    .setSeverityText("WARN")
                    .setBody("payment retry scheduled")
                    .setAttribute(stringKey("order.id"), "A123")
                    .setAttribute(longKey("attempt"), 2L)
                    .emit();

            assertThat(logExporter.getFinishedLogItems()).hasSize(1);

            LogsRequestMarshaler marshaler =
                    LogsRequestMarshaler.create(logExporter.getFinishedLogItems());
            assertBinarySizeMatches(marshaler);
            assertThat(jsonOf(marshaler)).contains(
                    "\"resourceLogs\"",
                    "\"scopeLogs\"",
                    "\"logRecords\"",
                    "\"timeUnixNano\":\"1234567890\"",
                    "\"severityNumber\":\"SEVERITY_NUMBER_WARN\"",
                    "\"severityText\":\"WARN\"",
                    "\"body\":{\"stringValue\":\"payment retry scheduled\"}",
                    "\"key\":\"order.id\"",
                    "\"stringValue\":\"A123\"",
                    "\"traceId\":\"" + TRACE_ID + "\"",
                    "\"spanId\":\"" + SPAN_ID + "\"");
        } finally {
            loggerProvider.close();
        }
    }

    private static Resource testResource() {
        return Resource.create(Attributes.builder()
                .put(stringKey("service.name"), "checkout-service")
                .put(stringKey("deployment.environment"), "test")
                .build());
    }

    private static SpanContext spanContext(String spanId) {
        return SpanContext.createFromRemoteParent(
                TRACE_ID,
                spanId,
                TraceFlags.getSampled(),
                TraceState.builder().put("vendor", "value").build());
    }

    private static void assertBinarySizeMatches(Marshaler marshaler) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        marshaler.writeBinaryTo(output);
        assertThat(output.toByteArray()).hasSize(marshaler.getBinarySerializedSize());
    }

    private static String jsonOf(Marshaler marshaler) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        marshaler.writeJsonTo(output);
        return output.toString(StandardCharsets.UTF_8);
    }

    private static String jsonOf(KeyValueMarshaler[] marshalers) throws IOException {
        StringBuilder json = new StringBuilder();
        for (KeyValueMarshaler marshaler : marshalers) {
            assertBinarySizeMatches(marshaler);
            json.append(jsonOf(marshaler));
        }
        return json.toString();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
