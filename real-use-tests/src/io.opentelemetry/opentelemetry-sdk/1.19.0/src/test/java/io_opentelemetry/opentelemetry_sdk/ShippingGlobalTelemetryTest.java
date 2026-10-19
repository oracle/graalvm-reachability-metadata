/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_sdk;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises global OpenTelemetry registration: `OpenTelemetrySdkBuilder.buildAndRegisterGlobal`
 * and the `GlobalOpenTelemetry` autoconfiguration fallback serving API-only instrumentation.
 *
 * A shipping service bootstraps its SDK once and registers it as the global instance. A carrier
 * client library, written only against the API, never sees that SDK: it obtains a versioned,
 * schema-tagged tracer and meter from `GlobalOpenTelemetry`, records a counter, reports queue
 * depth and in-flight requests through one batch callback, and injects its outgoing request
 * headers through the global propagators. The test checks that the library's spans and metrics
 * reach the application's exporters under the library's own instrumentation scope, that the
 * global instance hides the SDK type, and that a second registration is rejected. A second
 * deployment registers nothing and instead lets the first `GlobalOpenTelemetry.get()` call
 * autoconfigure the SDK from `otel.*` system properties.
 */
class ShippingGlobalTelemetryTest {

    private static final String LIBRARY_NAME = "com.acme.carrier-client";

    private static final String LIBRARY_VERSION = "3.2.1";

    private static final String SCHEMA_URL = "https://opentelemetry.io/schemas/1.12.0";

    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");

    private static final AttributeKey<String> CARRIER = AttributeKey.stringKey("carrier.name");

    private static final AttributeKey<String> PARCEL_ID = AttributeKey.stringKey("parcel.id");

    private static final Map<String, String> AUTOCONFIGURATION = Map.of(
            "otel.service.name", "shipping-autoconfigured",
            "otel.traces.exporter", "none",
            "otel.metrics.exporter", "none",
            "otel.logs.exporter", "none",
            "otel.traces.sampler", "always_on",
            "otel.propagators", "b3multi");

    private InMemorySpanExporter spanExporter;

    private InMemoryMetricReader metricReader;

    private OpenTelemetrySdk sdk;

    @BeforeEach
    void clearGlobal() {
        GlobalOpenTelemetry.resetForTest();
    }

    @AfterEach
    void shutDown() {
        GlobalOpenTelemetry.resetForTest();
        if (sdk != null) {
            assertThat(sdk.getSdkTracerProvider().shutdown().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
            assertThat(sdk.getSdkMeterProvider().shutdown().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
        }
    }

    @Test
    void apiOnlyLibraryReportsThroughTheRegisteredSdk() {
        sdk = bootstrapShippingService();

        OpenTelemetry global = GlobalOpenTelemetry.get();
        assertThat(global).isNotSameAs(sdk).isNotInstanceOf(OpenTelemetrySdk.class);
        assertThat(GlobalOpenTelemetry.getPropagators().getTextMapPropagator().fields())
                .containsExactly("traceparent", "tracestate");

        CarrierClient client = new CarrierClient();
        Map<String, String> firstRequest = client.book("parcel-71", "northwind");
        Map<String, String> secondRequest = client.book("parcel-72", "northwind");
        Map<String, String> thirdRequest = client.book("parcel-73", "skyline");

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).extracting(SpanData::getName)
                .containsExactly("book northwind", "book northwind", "book skyline");
        for (SpanData span : spans) {
            assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
            assertThat(span.getResource().getAttribute(SERVICE_NAME)).isEqualTo("shipping-service");
            assertLibraryScope(span.getInstrumentationScopeInfo());
        }
        assertThat(spans.get(0).getAttributes().get(PARCEL_ID)).isEqualTo("parcel-71");
        assertThat(firstRequest.get("traceparent"))
                .isEqualTo("00-" + spans.get(0).getTraceId() + "-" + spans.get(0).getSpanId() + "-01");
        assertThat(secondRequest.get("traceparent")).contains(spans.get(1).getSpanId());
        assertThat(thirdRequest.get("traceparent")).contains(spans.get(2).getSpanId());

        client.waitingParcels = 9;
        client.inFlight = 2;
        Map<String, MetricData> metrics = metricReader.collectAllMetrics().stream()
                .collect(Collectors.toMap(MetricData::getName, Function.identity()));
        assertThat(metrics).containsOnlyKeys("carrier.bookings", "carrier.queue.depth", "carrier.requests.in_flight");
        for (MetricData metric : metrics.values()) {
            assertThat(metric.getResource().getAttribute(SERVICE_NAME)).isEqualTo("shipping-service");
            assertLibraryScope(metric.getInstrumentationScopeInfo());
        }

        Map<String, Long> bookingsByCarrier = valuesBy(metrics.get("carrier.bookings").getLongSumData().getPoints());
        assertThat(bookingsByCarrier).containsOnly(Map.entry("northwind", 2L), Map.entry("skyline", 1L));
        assertThat(metrics.get("carrier.bookings").getUnit()).isEqualTo("{booking}");
        assertThat(metrics.get("carrier.queue.depth").getLongGaugeData().getPoints())
                .singleElement()
                .extracting(LongPointData::getValue)
                .isEqualTo(9L);
        assertThat(metrics.get("carrier.requests.in_flight").getLongSumData().getPoints())
                .singleElement()
                .extracting(LongPointData::getValue)
                .isEqualTo(2L);
        assertThat(client.batchCallbackRuns).isEqualTo(1);

        client.close();
        assertThat(metricReader.collectAllMetrics()).extracting(MetricData::getName)
                .containsExactly("carrier.bookings");
        assertThat(client.batchCallbackRuns).isEqualTo(1);
    }

    @Test
    void secondRegistrationIsRejectedAndTheFirstSdkStaysInPlace() {
        sdk = bootstrapShippingService();
        assertThatThrownBy(() -> OpenTelemetrySdk.builder().buildAndRegisterGlobal())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("GlobalOpenTelemetry.set has already been called")
                .hasCauseInstanceOf(Throwable.class);
        assertThat(GlobalOpenTelemetry.get()).isNotInstanceOf(OpenTelemetrySdk.class);

        new CarrierClient().book("parcel-90", "northwind");
        assertThat(spanExporter.getFinishedSpanItems()).extracting(SpanData::getName)
                .containsExactly("book northwind");
    }

    @Test
    void firstGlobalAccessAutoconfiguresTheSdkFromSystemProperties() {
        Map<String, String> previous = new LinkedHashMap<>();
        AUTOCONFIGURATION.forEach((key, value) -> previous.put(key, System.setProperty(key, value)));
        try {
            CarrierClient client = new CarrierClient();
            Map<String, String> request = client.book("parcel-55", "skyline");

            assertThat(GlobalOpenTelemetry.get()).isNotInstanceOf(OpenTelemetrySdk.class);
            assertThat(GlobalOpenTelemetry.getPropagators().getTextMapPropagator().fields())
                    .contains("X-B3-TraceId", "X-B3-SpanId", "X-B3-Sampled");
            assertThat(request).doesNotContainKey("traceparent");
            assertThat(request.get("X-B3-TraceId")).hasSize(32).isNotEqualTo("0".repeat(32));
            assertThat(request.get("X-B3-SpanId")).hasSize(16).isNotEqualTo("0".repeat(16));
            assertThat(request.get("X-B3-Sampled")).isEqualTo("1");
            assertThatThrownBy(() -> GlobalOpenTelemetry.set(OpenTelemetry.noop()))
                    .isInstanceOf(IllegalStateException.class);
            client.close();
        } finally {
            previous.forEach((key, value) -> {
                if (value == null) {
                    System.clearProperty(key);
                } else {
                    System.setProperty(key, value);
                }
            });
        }
    }

    private OpenTelemetrySdk bootstrapShippingService() {
        spanExporter = InMemorySpanExporter.create();
        metricReader = InMemoryMetricReader.create();
        Resource resource = Resource.getDefault().merge(Resource.create(Attributes.of(SERVICE_NAME, "shipping-service")));
        return OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .setResource(resource)
                        .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                        .build())
                .setMeterProvider(SdkMeterProvider.builder()
                        .setResource(resource)
                        .registerMetricReader(metricReader)
                        .build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();
    }

    private static void assertLibraryScope(InstrumentationScopeInfo scope) {
        assertThat(scope.getName()).isEqualTo(LIBRARY_NAME);
        assertThat(scope.getVersion()).isEqualTo(LIBRARY_VERSION);
        assertThat(scope.getSchemaUrl()).isEqualTo(SCHEMA_URL);
    }

    private static Map<String, Long> valuesBy(Collection<LongPointData> points) {
        return points.stream().collect(Collectors.toMap(
                point -> point.getAttributes().get(CARRIER), LongPointData::getValue));
    }

    /** A carrier client library that depends only on the OpenTelemetry API and the global instance. */
    static final class CarrierClient implements AutoCloseable {

        private static final TextMapSetter<Map<String, String>> HEADERS = Map::put;

        private volatile long waitingParcels;

        private volatile long inFlight;

        private volatile int batchCallbackRuns;

        private final Tracer tracer = GlobalOpenTelemetry.tracerBuilder(LIBRARY_NAME)
                .setInstrumentationVersion(LIBRARY_VERSION)
                .setSchemaUrl(SCHEMA_URL)
                .build();

        private final Meter meter = GlobalOpenTelemetry.meterBuilder(LIBRARY_NAME)
                .setInstrumentationVersion(LIBRARY_VERSION)
                .setSchemaUrl(SCHEMA_URL)
                .build();

        private final LongCounter bookings = meter.counterBuilder("carrier.bookings")
                .setUnit("{booking}")
                .setDescription("Parcels booked with a carrier")
                .build();

        private final ObservableLongMeasurement queueDepth = meter.gaugeBuilder("carrier.queue.depth")
                .ofLongs()
                .buildObserver();

        private final ObservableLongMeasurement inFlightRequests = meter.upDownCounterBuilder("carrier.requests.in_flight")
                .buildObserver();

        private final BatchCallback batchCallback = meter.batchCallback(() -> {
            batchCallbackRuns++;
            queueDepth.record(waitingParcels);
            inFlightRequests.record(inFlight);
        }, queueDepth, inFlightRequests);

        Map<String, String> book(String parcelId, String carrier) {
            Map<String, String> headers = new LinkedHashMap<>();
            Span span = tracer.spanBuilder("book " + carrier)
                    .setSpanKind(SpanKind.CLIENT)
                    .setAttribute(PARCEL_ID, parcelId)
                    .setAttribute(CARRIER, carrier)
                    .startSpan();
            try (Scope ignored = span.makeCurrent()) {
                GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
                        .inject(Context.current(), headers, HEADERS);
                bookings.add(1, Attributes.of(CARRIER, carrier));
            } finally {
                span.end();
            }
            return headers;
        }

        @Override
        public void close() {
            batchCallback.close();
        }
    }
}
