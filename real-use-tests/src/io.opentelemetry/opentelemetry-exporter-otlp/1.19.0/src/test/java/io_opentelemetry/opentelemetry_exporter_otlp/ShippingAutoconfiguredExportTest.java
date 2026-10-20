/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_exporter_otlp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.AggregationTemporality;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exporter configuration from `otel.exporter.otlp.*` properties through the SDK autoconfigure module.
 *
 * A shipping service does not build its exporters in code: like most deployed applications it
 * hands a set of `otel.*` properties to `AutoConfiguredOpenTelemetrySdk`, which selects the OTLP
 * protocol and creates the span and metric exporters with the configured endpoint, headers,
 * compression, temporality, and retry policy. The collector is a MockWebServer that decodes the
 * OTLP/HTTP requests, so the test asserts on what a collector receives for shared and per-signal
 * settings, on a transient outage with and without retry, and on a misconfigured endpoint.
 */
public class ShippingAutoconfiguredExportTest {

    private static final long WAIT_SECONDS = 20;

    private static final AttributeKey<String> PARCEL_ID = AttributeKey.stringKey("shipping.parcel_id");

    private static final AttributeKey<String> CARRIER = AttributeKey.stringKey("shipping.carrier");

    private MockWebServer collector;

    private CollectorResponses responses;

    private OpenTelemetrySdk sdk;

    @BeforeEach
    void startCollector() throws IOException {
        responses = new CollectorResponses();
        collector = new MockWebServer();
        collector.setDispatcher(responses);
        collector.start();
    }

    @AfterEach
    void stopCollector() throws IOException {
        if (sdk != null) {
            sdk.getSdkTracerProvider().shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS);
            sdk.getSdkMeterProvider().shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS);
        }
        collector.shutdown();
    }

    @Test
    void exportsBothSignalsWithTheSharedOtlpProperties() throws Exception {
        Map<String, String> properties = baseProperties();
        properties.put("otel.exporter.otlp.endpoint", collectorBaseUrl());
        properties.put("otel.exporter.otlp.headers", "x-tenant=acme-logistics,x-region=eu-west");
        properties.put("otel.exporter.otlp.timeout", "10000");
        sdk = autoconfigure(properties).getOpenTelemetrySdk();
        ShippingService shipping = new ShippingService(sdk);

        shipping.dispatch("parcel-1", "dhl");
        shipping.dispatch("parcel-2", "ups");
        shipping.dispatch("parcel-3", "dhl");
        assertThat(sdk.getSdkTracerProvider().forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess())
                .isTrue();
        assertThat(sdk.getSdkMeterProvider().forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess())
                .isTrue();

        Map<String, RecordedRequest> requests = takeRequestsByPath(2);
        assertThat(requests).containsOnlyKeys("/v1/traces", "/v1/metrics");
        for (RecordedRequest request : requests.values()) {
            assertThat(request.getMethod()).isEqualTo("POST");
            assertThat(request.getHeader("Content-Type")).startsWith("application/x-protobuf");
            assertThat(request.getHeader("x-tenant")).isEqualTo("acme-logistics");
            assertThat(request.getHeader("x-region")).isEqualTo("eu-west");
            assertThat(request.getHeader("Content-Encoding")).isNull();
        }

        ExportTraceServiceRequest traces =
                ExportTraceServiceRequest.parseFrom(requests.get("/v1/traces").getBody().readByteArray());
        assertThat(attributes(traces.getResourceSpans(0).getResource().getAttributesList()))
                .containsEntry(ResourceAttributes.SERVICE_NAME.getKey(), "shipping")
                .containsEntry(ResourceAttributes.SERVICE_NAMESPACE.getKey(), "logistics")
                .containsEntry(ResourceAttributes.DEPLOYMENT_ENVIRONMENT.getKey(), "staging");
        List<io.opentelemetry.proto.trace.v1.Span> spans = spans(traces);
        assertThat(spans).extracting(io.opentelemetry.proto.trace.v1.Span::getName).containsOnly("dispatch-parcel");
        assertThat(spans).extracting(span -> attributes(span.getAttributesList()).get(PARCEL_ID.getKey()))
                .containsExactlyInAnyOrder("parcel-1", "parcel-2", "parcel-3");

        ExportMetricsServiceRequest metrics =
                ExportMetricsServiceRequest.parseFrom(requests.get("/v1/metrics").getBody().readByteArray());
        Metric dispatched = metricsByName(metrics).get("shipping.parcels.dispatched");
        assertThat(dispatched).isNotNull();
        assertThat(dispatched.getSum().getAggregationTemporality())
                .isEqualTo(AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE);
        assertThat(pointsByCarrier(dispatched.getSum().getDataPointsList()))
                .containsEntry("dhl", 2L)
                .containsEntry("ups", 1L)
                .hasSize(2);
    }

    @Test
    void appliesPerSignalPropertiesOverTheSharedOnes() throws Exception {
        Map<String, String> properties = baseProperties();
        properties.put("otel.exporter.otlp.endpoint", collectorBaseUrl());
        properties.put("otel.exporter.otlp.headers", "x-tenant=acme-logistics");
        properties.put("otel.exporter.otlp.traces.endpoint", collector.url("/ingest/shipping/traces").toString());
        properties.put("otel.exporter.otlp.traces.compression", "gzip");
        properties.put("otel.exporter.otlp.metrics.headers", "x-tenant=metrics-team");
        properties.put("otel.exporter.otlp.metrics.temporality.preference", "delta");
        sdk = autoconfigure(properties).getOpenTelemetrySdk();
        ShippingService shipping = new ShippingService(sdk);

        shipping.dispatch("parcel-7", "fedex");
        shipping.dispatch("parcel-8", "fedex");
        assertThat(sdk.getSdkTracerProvider().forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess())
                .isTrue();
        assertThat(sdk.getSdkMeterProvider().forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess())
                .isTrue();
        Map<String, RecordedRequest> firstRound = takeRequestsByPath(2);
        shipping.dispatch("parcel-9", "fedex");
        assertThat(sdk.getSdkMeterProvider().forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess())
                .isTrue();
        RecordedRequest secondMetrics = collector.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS);

        assertThat(firstRound).containsOnlyKeys("/ingest/shipping/traces", "/v1/metrics");
        RecordedRequest tracesRequest = firstRound.get("/ingest/shipping/traces");
        assertThat(tracesRequest.getHeader("Content-Encoding")).isEqualTo("gzip");
        assertThat(tracesRequest.getHeader("x-tenant")).isEqualTo("acme-logistics");
        ExportTraceServiceRequest traces = ExportTraceServiceRequest.parseFrom(gunzip(tracesRequest));
        assertThat(spans(traces)).extracting(span -> attributes(span.getAttributesList()).get(PARCEL_ID.getKey()))
                .containsExactlyInAnyOrder("parcel-7", "parcel-8");

        RecordedRequest firstMetrics = firstRound.get("/v1/metrics");
        assertThat(firstMetrics.getHeader("x-tenant")).isEqualTo("metrics-team");
        assertThat(firstMetrics.getHeader("Content-Encoding")).isNull();
        assertThat(secondMetrics).isNotNull();
        assertThat(secondMetrics.getPath()).isEqualTo("/v1/metrics");
        List<Long> fedexPerCollection = new ArrayList<>();
        for (RecordedRequest request : List.of(firstMetrics, secondMetrics)) {
            Metric dispatched = metricsByName(ExportMetricsServiceRequest.parseFrom(request.getBody().readByteArray()))
                    .get("shipping.parcels.dispatched");
            assertThat(dispatched.getSum().getAggregationTemporality())
                    .isEqualTo(AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA);
            fedexPerCollection.add(pointsByCarrier(dispatched.getSum().getDataPointsList()).get("fedex"));
        }
        assertThat(fedexPerCollection).containsExactly(2L, 1L);
    }

    @Test
    void retriesATransientCollectorOutageWhenRetryIsEnabled() throws Exception {
        responses.failNextTraceExports(1, 503);
        Map<String, String> properties = baseProperties();
        properties.put("otel.metrics.exporter", "none");
        properties.put("otel.exporter.otlp.endpoint", collectorBaseUrl());
        properties.put("otel.experimental.exporter.otlp.retry.enabled", "true");
        ExportOutcomes outcomes = new ExportOutcomes();
        sdk = autoconfigure(properties, outcomes).getOpenTelemetrySdk();
        ShippingService shipping = new ShippingService(sdk);

        shipping.dispatch("parcel-21", "dhl");
        sdk.getSdkTracerProvider().forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS);

        assertThat(outcomes.results).containsExactly(true);
        RecordedRequest rejected = collector.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS);
        RecordedRequest retried = collector.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(rejected).isNotNull();
        assertThat(retried).isNotNull();
        assertThat(retried.getPath()).isEqualTo("/v1/traces");
        io.opentelemetry.proto.trace.v1.Span first =
                spans(ExportTraceServiceRequest.parseFrom(rejected.getBody().readByteArray())).get(0);
        io.opentelemetry.proto.trace.v1.Span second =
                spans(ExportTraceServiceRequest.parseFrom(retried.getBody().readByteArray())).get(0);
        assertThat(second.getSpanId()).isEqualTo(first.getSpanId());
        assertThat(attributes(second.getAttributesList())).containsEntry(PARCEL_ID.getKey(), "parcel-21");
    }

    @Test
    void reportsACollectorOutageWhenRetryIsNotEnabled() throws Exception {
        responses.failNextTraceExports(1, 503);
        Map<String, String> properties = baseProperties();
        properties.put("otel.metrics.exporter", "none");
        properties.put("otel.exporter.otlp.endpoint", collectorBaseUrl());
        ExportOutcomes outcomes = new ExportOutcomes();
        sdk = autoconfigure(properties, outcomes).getOpenTelemetrySdk();
        ShippingService shipping = new ShippingService(sdk);

        shipping.dispatch("parcel-31", "ups");
        sdk.getSdkTracerProvider().forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS);
        shipping.dispatch("parcel-32", "ups");
        sdk.getSdkTracerProvider().forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS);

        assertThat(outcomes.results).containsExactly(false, true);
        assertThat(collector.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS)).isNotNull();
        RecordedRequest delivered = collector.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(delivered).isNotNull();
        assertThat(spans(ExportTraceServiceRequest.parseFrom(delivered.getBody().readByteArray())))
                .extracting(span -> attributes(span.getAttributesList()).get(PARCEL_ID.getKey()))
                .containsExactly("parcel-32");
        assertThat(collector.getRequestCount()).isEqualTo(2);
    }

    @Test
    void rejectsAnEndpointWithANonHttpSchemeAtStartup() {
        Map<String, String> properties = baseProperties();
        properties.put("otel.exporter.otlp.endpoint", "grpc://collector.internal:4318");

        assertThatThrownBy(() -> autoconfigure(properties))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("OTLP endpoint must be a valid URL: grpc://collector.internal:4318");
    }

    private String collectorBaseUrl() {
        String url = collector.url("/").toString();
        return url.substring(0, url.length() - 1);
    }

    private Map<String, RecordedRequest> takeRequestsByPath(int count) throws InterruptedException {
        Map<String, RecordedRequest> requests = new HashMap<>();
        for (int i = 0; i < count; i++) {
            RecordedRequest request = collector.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            requests.put(request.getPath(), request);
        }
        return requests;
    }

    /// The `otel.*` properties every deployment of the shipping service sets.
    private static Map<String, String> baseProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put("otel.service.name", "shipping");
        properties.put("otel.resource.attributes", "service.namespace=logistics,deployment.environment=staging");
        properties.put("otel.traces.exporter", "otlp");
        properties.put("otel.metrics.exporter", "otlp");
        properties.put("otel.logs.exporter", "none");
        properties.put("otel.exporter.otlp.protocol", "http/protobuf");
        properties.put("otel.bsp.schedule.delay", "600000");
        properties.put("otel.metric.export.interval", "600000");
        return properties;
    }

    private static AutoConfiguredOpenTelemetrySdk autoconfigure(Map<String, String> properties) {
        return AutoConfiguredOpenTelemetrySdk.builder()
                .addPropertiesSupplier(() -> properties)
                .setResultAsGlobal(false)
                .registerShutdownHook(false)
                .build();
    }

    private static AutoConfiguredOpenTelemetrySdk autoconfigure(
            Map<String, String> properties, ExportOutcomes outcomes) {
        return AutoConfiguredOpenTelemetrySdk.builder()
                .addPropertiesSupplier(() -> properties)
                .addSpanExporterCustomizer((exporter, config) -> outcomes.wrap(exporter))
                .setResultAsGlobal(false)
                .registerShutdownHook(false)
                .build();
    }

    private static byte[] gunzip(RecordedRequest request) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(request.getBody().readByteArray()))) {
            return in.readAllBytes();
        }
    }

    private static List<io.opentelemetry.proto.trace.v1.Span> spans(ExportTraceServiceRequest export) {
        return export.getResourceSpansList().stream()
                .flatMap(resourceSpans -> resourceSpans.getScopeSpansList().stream())
                .flatMap(scopeSpans -> scopeSpans.getSpansList().stream())
                .collect(Collectors.toList());
    }

    private static Map<String, Metric> metricsByName(ExportMetricsServiceRequest export) {
        return export.getResourceMetricsList().stream()
                .flatMap(resourceMetrics -> resourceMetrics.getScopeMetricsList().stream())
                .flatMap(scopeMetrics -> scopeMetrics.getMetricsList().stream())
                .collect(Collectors.toMap(Metric::getName, Function.identity(), (first, second) -> first));
    }

    private static Map<String, Long> pointsByCarrier(List<NumberDataPoint> points) {
        return points.stream().collect(Collectors.toMap(
                point -> attributes(point.getAttributesList()).get(CARRIER.getKey()), NumberDataPoint::getAsInt));
    }

    private static Map<String, String> attributes(List<KeyValue> keyValues) {
        return keyValues.stream()
                .collect(Collectors.toMap(KeyValue::getKey, keyValue -> keyValue.getValue().getStringValue()));
    }

    /// The application code under observation: each dispatched parcel is a span and a counter increment.
    private static final class ShippingService {

        private final Tracer tracer;

        private final LongCounter dispatched;

        ShippingService(OpenTelemetrySdk sdk) {
            tracer = sdk.getTracer("shipping-service");
            dispatched = sdk.getMeter("shipping-service")
                    .counterBuilder("shipping.parcels.dispatched")
                    .setDescription("Parcels handed over to a carrier")
                    .setUnit("{parcel}")
                    .build();
        }

        void dispatch(String parcelId, String carrier) {
            Span span = tracer.spanBuilder("dispatch-parcel")
                    .setAttribute(PARCEL_ID, parcelId)
                    .setAttribute(CARRIER, carrier)
                    .startSpan();
            try {
                dispatched.add(1, Attributes.of(CARRIER, carrier));
            } finally {
                span.end();
            }
        }
    }

    /// The collector's answers: success, unless a number of trace exports is scripted to fail.
    private static final class CollectorResponses extends Dispatcher {

        private final AtomicInteger pendingTraceFailures = new AtomicInteger();

        private volatile int failureStatus = 503;

        void failNextTraceExports(int count, int status) {
            failureStatus = status;
            pendingTraceFailures.set(count);
        }

        @Override
        public MockResponse dispatch(RecordedRequest request) {
            if (request.getPath().endsWith("/traces") && pendingTraceFailures.getAndDecrement() > 0) {
                return new MockResponse().setResponseCode(failureStatus).setBody("collector overloaded");
            }
            return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/x-protobuf");
        }
    }

    /// Wraps the autoconfigured span exporter to record each export outcome for the health page.
    private static final class ExportOutcomes {

        private final List<Boolean> results = new CopyOnWriteArrayList<>();

        SpanExporter wrap(SpanExporter delegate) {
            return new SpanExporter() {
                @Override
                public CompletableResultCode export(Collection<SpanData> spans) {
                    CompletableResultCode result = delegate.export(spans);
                    result.whenComplete(() -> results.add(result.isSuccess()));
                    return result;
                }

                @Override
                public CompletableResultCode flush() {
                    return delegate.flush();
                }

                @Override
                public CompletableResultCode shutdown() {
                    return delegate.shutdown();
                }
            };
        }
    }
}
