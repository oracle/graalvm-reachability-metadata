/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_sdk;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageEntryMetadata;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.View;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanLimits;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

public class Opentelemetry_sdkTest {
    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");
    private static final AttributeKey<String> ROUTE = AttributeKey.stringKey("http.route");
    private static final AttributeKey<Long> STATUS = AttributeKey.longKey("http.status_code");
    private static final AttributeKey<String> ENDPOINT = AttributeKey.stringKey("endpoint");
    private static final AttributeKey<String> WORKER = AttributeKey.stringKey("worker");
    private static final TextMapSetter<Map<String, String>> MAP_SETTER =
            (carrier, key, value) -> carrier.put(key, value);
    private static final TextMapGetter<Map<String, String>> MAP_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier.get(key);
        }
    };

    @Test
    void sdkBuilderUsesConfiguredTracerAndMeterProviders() {
        RecordingSpanExporter spanExporter = new RecordingSpanExporter();
        RecordingMetricExporter metricExporter = new RecordingMetricExporter();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(PeriodicMetricReader.create(metricExporter))
                .build();

        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setMeterProvider(meterProvider)
                .build();

        assertThat(sdk.getSdkTracerProvider()).isSameAs(tracerProvider);
        assertThat(sdk.getSdkMeterProvider()).isSameAs(meterProvider);
        assertThat(sdk.getTracerProvider().tracerBuilder("facade-tracer").build()).isNotNull();
        assertThat(sdk.getMeterProvider().meterBuilder("facade-meter").build()).isNotNull();
        assertThat(sdk.getPropagators()).isNotNull();
        assertThat(sdk.toString()).contains("OpenTelemetrySdk");

        assertThat(tracerProvider.shutdown().join(5, TimeUnit.SECONDS).isSuccess()).isTrue();
        assertThat(meterProvider.shutdown().join(5, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void configuredTextMapPropagatorsInjectAndExtractTraceContextAndBaggage() {
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setPropagators(ContextPropagators.create(TextMapPropagator.composite(
                        W3CTraceContextPropagator.getInstance(),
                        W3CBaggagePropagator.getInstance())))
                .build();
        SpanContext spanContext = SpanContext.create(
                "00000000000000000000000000000055",
                "0000000000000066",
                TraceFlags.getSampled(),
                TraceState.builder().put("vendor", "value").build());
        Baggage baggage = Baggage.builder()
                .put("tenant", "acme", BaggageEntryMetadata.create("source=synthetic"))
                .put("workflow", "checkout")
                .build();
        Context context = baggage.storeInContext(Context.root().with(Span.wrap(spanContext)));
        Map<String, String> carrier = new LinkedHashMap<>();

        sdk.getPropagators().getTextMapPropagator().inject(context, carrier, MAP_SETTER);
        Context extracted = sdk.getPropagators().getTextMapPropagator().extract(Context.root(), carrier, MAP_GETTER);

        assertThat(carrier).containsKeys("traceparent", "tracestate", "baggage");
        SpanContext extractedSpanContext = Span.fromContext(extracted).getSpanContext();
        assertThat(extractedSpanContext.getTraceId()).isEqualTo(spanContext.getTraceId());
        assertThat(extractedSpanContext.getSpanId()).isEqualTo(spanContext.getSpanId());
        assertThat(extractedSpanContext.isSampled()).isTrue();
        assertThat(extractedSpanContext.isRemote()).isTrue();
        assertThat(extractedSpanContext.getTraceState().get("vendor")).isEqualTo("value");
        Baggage extractedBaggage = Baggage.fromContext(extracted);
        assertThat(extractedBaggage.getEntryValue("tenant")).isEqualTo("acme");
        assertThat(extractedBaggage.asMap().get("tenant").getMetadata().getValue()).isEqualTo("source=synthetic");
        assertThat(extractedBaggage.getEntryValue("workflow")).isEqualTo("checkout");
    }

    @Test
    void simpleSpanProcessorExportsParentChildSpansWithEventsLinksStatusAndResource() {
        RecordingSpanExporter exporter = new RecordingSpanExporter();
        Resource resource = Resource.builder()
                .put(SERVICE_NAME, "checkout-service")
                .put("deployment.environment", "test")
                .build();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .setSampler(Sampler.alwaysOn())
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        Tracer tracer = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build()
                .getTracerProvider()
                .tracerBuilder("checkout-instrumentation")
                .setInstrumentationVersion("1.2.3")
                .build();
        SpanContext remoteParent = SpanContext.createFromRemoteParent(
                "00000000000000000000000000000011",
                "0000000000000022",
                TraceFlags.getSampled(),
                TraceState.builder().put("vendor", "value").build());
        SpanContext linkedContext = SpanContext.create(
                "00000000000000000000000000000033",
                "0000000000000044",
                TraceFlags.getDefault(),
                TraceState.getDefault());

        Span serverSpan = tracer.spanBuilder("incoming-request")
                .setParent(Context.root().with(Span.wrap(remoteParent)))
                .setSpanKind(SpanKind.SERVER)
                .setAttribute(ROUTE, "/orders/{id}")
                .setAttribute("http.method", "POST")
                .addLink(linkedContext, Attributes.of(ENDPOINT, "inventory"))
                .startSpan();
        SpanContext serverContext = serverSpan.getSpanContext();
        try (Scope scope = serverSpan.makeCurrent()) {
            serverSpan.addEvent("request.accepted", Attributes.of(STATUS, 202L));
            Span childSpan = tracer.spanBuilder("repository-call")
                    .setSpanKind(SpanKind.CLIENT)
                    .setAttribute("db.system", "h2")
                    .startSpan();
            childSpan.addEvent("query.executed");
            childSpan.end();
        }
        serverSpan.recordException(new IllegalStateException("boom"));
        serverSpan.setStatus(StatusCode.ERROR, "request failed");
        serverSpan.updateName("incoming-request-renamed");
        serverSpan.end();

        assertThat(tracerProvider.forceFlush().join(5, TimeUnit.SECONDS).isSuccess()).isTrue();
        assertThat(tracerProvider.shutdown().join(5, TimeUnit.SECONDS).isSuccess()).isTrue();
        assertThat(exporter.isShutdown()).isTrue();

        SpanData serverData = exporter.findSpan("incoming-request-renamed").orElseThrow(AssertionError::new);
        SpanData childData = exporter.findSpan("repository-call").orElseThrow(AssertionError::new);

        assertThat(serverData.getKind()).isEqualTo(SpanKind.SERVER);
        assertThat(serverData.getSpanContext().getTraceId()).isEqualTo(remoteParent.getTraceId());
        assertThat(serverData.getParentSpanContext().getSpanId()).isEqualTo(remoteParent.getSpanId());
        assertThat(serverData.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(serverData.getStatus().getDescription()).isEqualTo("request failed");
        assertThat(serverData.getAttributes().get(ROUTE)).isEqualTo("/orders/{id}");
        assertThat(serverData.getResource().getAttribute(SERVICE_NAME)).isEqualTo("checkout-service");
        assertThat(serverData.getInstrumentationScopeInfo().getName()).isEqualTo("checkout-instrumentation");
        assertThat(serverData.getInstrumentationScopeInfo().getVersion()).isEqualTo("1.2.3");
        assertThat(serverData.getEvents()).extracting(EventData::getName)
                .contains("request.accepted", "exception");
        assertThat(serverData.getLinks()).hasSize(1);
        LinkData linkData = serverData.getLinks().get(0);
        assertThat(linkData.getSpanContext().getSpanId()).isEqualTo(linkedContext.getSpanId());
        assertThat(linkData.getAttributes().get(ENDPOINT)).isEqualTo("inventory");

        assertThat(childData.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(childData.getParentSpanContext().getSpanId()).isEqualTo(serverContext.getSpanId());
        assertThat(childData.getSpanContext().getTraceId()).isEqualTo(serverContext.getTraceId());
        assertThat(childData.getEvents()).extracting(EventData::getName).containsExactly("query.executed");
    }

    @Test
    void parentBasedSamplerDropsRootSpansAndKeepsSampledRemoteChildren() {
        RecordingSpanExporter exporter = new RecordingSpanExporter();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setSampler(Sampler.parentBased(Sampler.alwaysOff()))
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        Tracer tracer = tracerProvider.tracerBuilder("sampler-tracer").build();
        SpanContext sampledRemoteParent = SpanContext.createFromRemoteParent(
                "00000000000000000000000000000077",
                "0000000000000088",
                TraceFlags.getSampled(),
                TraceState.getDefault());

        Span droppedRootSpan = tracer.spanBuilder("dropped-root-span").startSpan();
        droppedRootSpan.end();
        Span sampledChildSpan = tracer.spanBuilder("sampled-remote-child")
                .setParent(Context.root().with(Span.wrap(sampledRemoteParent)))
                .startSpan();
        sampledChildSpan.end();

        assertThat(droppedRootSpan.getSpanContext().isSampled()).isFalse();
        assertThat(sampledChildSpan.getSpanContext().isSampled()).isTrue();
        assertThat(tracerProvider.shutdown().join(5, TimeUnit.SECONDS).isSuccess()).isTrue();

        assertThat(exporter.findSpan("dropped-root-span")).isEmpty();
        SpanData childData = exporter.findSpan("sampled-remote-child").orElseThrow(AssertionError::new);
        assertThat(childData.getParentSpanContext().getSpanId()).isEqualTo(sampledRemoteParent.getSpanId());
        assertThat(childData.getSpanContext().getTraceId()).isEqualTo(sampledRemoteParent.getTraceId());
    }

    @Test
    void spanLimitsRetainTelemetryTotalsWhileDroppingExcessAttributesEventsAndLinks() {
        RecordingSpanExporter exporter = new RecordingSpanExporter();
        SpanLimits limits = SpanLimits.builder()
                .setMaxNumberOfAttributes(2)
                .setMaxNumberOfEvents(1)
                .setMaxNumberOfLinks(1)
                .setMaxNumberOfAttributesPerEvent(1)
                .setMaxNumberOfAttributesPerLink(1)
                .build();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setSpanLimits(limits)
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        Tracer tracer = tracerProvider.tracerBuilder("limited-tracer").build();
        SpanContext firstLink = SpanContext.create(
                "00000000000000000000000000000100",
                "0000000000000101",
                TraceFlags.getSampled(),
                TraceState.getDefault());
        SpanContext secondLink = SpanContext.create(
                "00000000000000000000000000000200",
                "0000000000000202",
                TraceFlags.getSampled(),
                TraceState.getDefault());

        Span span = tracer.spanBuilder("limited-span")
                .setAttribute("attribute.one", "one")
                .setAttribute("attribute.two", "two")
                .setAttribute("attribute.three", "three")
                .addLink(firstLink, Attributes.of(ENDPOINT, "first", WORKER, "alpha"))
                .addLink(secondLink, Attributes.of(ENDPOINT, "second", WORKER, "beta"))
                .startSpan();
        span.addEvent("first-event", Attributes.of(ENDPOINT, "first", WORKER, "alpha"));
        span.addEvent("second-event", Attributes.of(ENDPOINT, "second", WORKER, "beta"));
        span.end();

        assertThat(tracerProvider.shutdown().join(5, TimeUnit.SECONDS).isSuccess()).isTrue();

        SpanData spanData = exporter.findSpan("limited-span").orElseThrow(AssertionError::new);
        assertThat(spanData.getAttributes().size()).isEqualTo(2);
        assertThat(spanData.getTotalAttributeCount()).isEqualTo(3);
        assertThat(spanData.getEvents()).hasSize(1);
        assertThat(spanData.getTotalRecordedEvents()).isEqualTo(2);
        assertThat(spanData.getEvents().get(0).getAttributes().size()).isEqualTo(1);
        assertThat(spanData.getEvents().get(0).getTotalAttributeCount()).isEqualTo(2);
        assertThat(spanData.getLinks()).hasSize(1);
        assertThat(spanData.getTotalRecordedLinks()).isEqualTo(2);
        assertThat(spanData.getLinks().get(0).getAttributes().size()).isEqualTo(1);
        assertThat(spanData.getLinks().get(0).getTotalAttributeCount()).isEqualTo(2);
    }

    @Test
    void metricReaderExportsCounterHistogramAndObservableGaugeWithViewsAndResource() {
        RecordingMetricExporter exporter = new RecordingMetricExporter();
        Resource resource = Resource.builder()
                .put(SERVICE_NAME, "metrics-service")
                .build();
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .setResource(resource)
                .registerView(
                        InstrumentSelector.builder()
                                .setType(InstrumentType.HISTOGRAM)
                                .setName("request.duration")
                                .build(),
                        View.builder()
                                .setName("request.duration.histogram")
                                .setDescription("renamed duration histogram")
                                .setAggregation(Aggregation.explicitBucketHistogram(Arrays.asList(5.0D, 10.0D)))
                                .build())
                .registerMetricReader(PeriodicMetricReader.create(exporter))
                .build();
        Meter meter = OpenTelemetrySdk.builder()
                .setMeterProvider(meterProvider)
                .build()
                .getMeterProvider()
                .meterBuilder("checkout-meter")
                .setInstrumentationVersion("4.5.6")
                .setSchemaUrl("https://schemas.example/metrics")
                .build();
        Attributes attributes = Attributes.of(ENDPOINT, "/checkout");
        AtomicLong queueDepth = new AtomicLong(3L);
        ObservableLongGauge gauge = meter.gaugeBuilder("queue.depth")
                .ofLongs()
                .setDescription("work queue depth")
                .setUnit("items")
                .buildWithCallback(measurement -> measurement.record(queueDepth.get(), attributes));

        try {
            LongCounter counter = meter.counterBuilder("requests.total")
                    .setDescription("processed requests")
                    .setUnit("1")
                    .build();
            DoubleHistogram histogram = meter.histogramBuilder("request.duration")
                    .setDescription("request duration")
                    .setUnit("ms")
                    .build();
            counter.add(5L, attributes);
            counter.add(3L, attributes);
            histogram.record(7.0D, attributes);
            histogram.record(12.0D, attributes);
            queueDepth.set(9L);

            assertThat(meterProvider.forceFlush().join(5, TimeUnit.SECONDS).isSuccess()).isTrue();
        } finally {
            gauge.close();
            assertThat(meterProvider.shutdown().join(5, TimeUnit.SECONDS).isSuccess()).isTrue();
        }

        MetricData counterData = exporter.findMetric("requests.total").orElseThrow(AssertionError::new);
        MetricData histogramData = exporter.findMetric("request.duration.histogram").orElseThrow(AssertionError::new);
        MetricData gaugeData = exporter.findMetric("queue.depth").orElseThrow(AssertionError::new);

        assertThat(counterData.getType()).isEqualTo(MetricDataType.LONG_SUM);
        assertThat(counterData.getDescription()).isEqualTo("processed requests");
        assertThat(counterData.getUnit()).isEqualTo("1");
        assertThat(counterData.getResource().getAttribute(SERVICE_NAME)).isEqualTo("metrics-service");
        assertThat(counterData.getInstrumentationScopeInfo().getName()).isEqualTo("checkout-meter");
        assertThat(counterData.getInstrumentationScopeInfo().getVersion()).isEqualTo("4.5.6");
        List<LongPointData> counterPoints = new ArrayList<>(counterData.getLongSumData().getPoints());
        assertThat(counterPoints).hasSize(1);
        assertThat(counterPoints.get(0).getValue()).isEqualTo(8L);
        assertThat(counterPoints.get(0).getAttributes().get(ENDPOINT)).isEqualTo("/checkout");
        assertThat(counterData.getLongSumData().isMonotonic()).isTrue();
        assertThat(counterData.getLongSumData().getAggregationTemporality())
                .isEqualTo(AggregationTemporality.CUMULATIVE);

        assertThat(histogramData.getType()).isEqualTo(MetricDataType.HISTOGRAM);
        assertThat(histogramData.getDescription()).isEqualTo("renamed duration histogram");
        List<HistogramPointData> histogramPoints = new ArrayList<>(histogramData.getHistogramData().getPoints());
        assertThat(histogramPoints).hasSize(1);
        assertThat(histogramPoints.get(0).getCount()).isEqualTo(2L);
        assertThat(histogramPoints.get(0).getSum()).isEqualTo(19.0D);
        assertThat(histogramPoints.get(0).getBoundaries()).containsExactly(5.0D, 10.0D);
        assertThat(histogramPoints.get(0).getCounts()).containsExactly(0L, 1L, 1L);

        assertThat(gaugeData.getType()).isEqualTo(MetricDataType.LONG_GAUGE);
        List<LongPointData> gaugePoints = new ArrayList<>(gaugeData.getLongGaugeData().getPoints());
        assertThat(gaugePoints).hasSize(1);
        assertThat(gaugePoints.get(0).getValue()).isEqualTo(9L);
        assertThat(exporter.isShutdown()).isTrue();
    }

    private static final class RecordingSpanExporter implements SpanExporter {
        private final List<SpanData> spans = new CopyOnWriteArrayList<>();
        private volatile boolean shutdown;

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            this.spans.addAll(spans);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            shutdown = true;
            return CompletableResultCode.ofSuccess();
        }

        Optional<SpanData> findSpan(String name) {
            return spans.stream()
                    .filter(span -> span.getName().equals(name))
                    .findFirst();
        }

        boolean isShutdown() {
            return shutdown;
        }
    }

    private static final class RecordingMetricExporter implements MetricExporter {
        private final List<MetricData> metrics = new CopyOnWriteArrayList<>();
        private volatile boolean shutdown;

        @Override
        public CompletableResultCode export(Collection<MetricData> metrics) {
            this.metrics.addAll(metrics);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            shutdown = true;
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
            return AggregationTemporality.CUMULATIVE;
        }

        Optional<MetricData> findMetric(String name) {
            return metrics.stream()
                    .filter(metric -> metric.getName().equals(name))
                    .reduce((previous, current) -> current);
        }

        boolean isShutdown() {
            return shutdown;
        }
    }
}
