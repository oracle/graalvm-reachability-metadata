/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_exporter_otlp;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Exemplar;
import io.opentelemetry.proto.metrics.v1.HistogramDataPoint;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.View;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanLimits;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Trace-to-metric correlation in OTLP payloads: metric exemplars and span links over OTLP/HTTP.
 *
 * A fraud-scoring service records how long each payment score takes and counts its decisions while
 * a span for the payment is current, so the exported metric points carry exemplars that point back
 * at that span. A view keeps only the decision on the counter; SDK 1.19 offers exemplars the
 * already-filtered attributes, so the dropped merchant reaches neither the point nor its exemplar,
 * while the exemplar still names the span to ask. A nightly settlement job links its batch span to
 * the payment traces it settles, under span limits that cap links and link attributes. Two
 * MockWebServers stand in for the collector's `/v1/metrics` and `/v1/traces` endpoints and decode
 * the protobuf requests, so the test asserts on the identifiers a backend would use to jump from
 * a latency spike to the trace behind it.
 */
public class FraudScoringCorrelationExportTest {

    private static final long WAIT_SECONDS = 20;

    private static final AttributeKey<String> ROUTE = AttributeKey.stringKey("fraud.route");

    private static final AttributeKey<String> DECISION = AttributeKey.stringKey("fraud.decision");

    private static final AttributeKey<String> MERCHANT = AttributeKey.stringKey("fraud.merchant");

    private static final AttributeKey<String> PAYMENT_ID = AttributeKey.stringKey("payment.id");

    private static final AttributeKey<String> SETTLEMENT_REGION = AttributeKey.stringKey("settlement.region");

    private static final Resource RESOURCE = Resource.getDefault().merge(Resource.create(Attributes.of(
            ResourceAttributes.SERVICE_NAME, "fraud-scoring")));

    private MockWebServer metricsCollector;

    private MockWebServer traceCollector;

    @BeforeEach
    void startCollectors() throws IOException {
        metricsCollector = new MockWebServer();
        metricsCollector.start();
        traceCollector = new MockWebServer();
        traceCollector.start();
    }

    @AfterEach
    void stopCollectors() throws IOException {
        metricsCollector.shutdown();
        traceCollector.shutdown();
    }

    @Test
    void attachesTheSampledPaymentSpanAsAHistogramExemplar() throws Exception {
        acceptMetrics();
        acceptTraces();
        SdkTracerProvider tracerProvider = tracerProvider(SpanLimits.getDefault());
        SdkMeterProvider meterProvider = meterProvider(SdkMeterProvider.builder());
        FraudScoringService scoring = new FraudScoringService(
                tracerProvider.get("fraud-scoring"), meterProvider.get("fraud-scoring"));

        SpanContext scored = scoring.score(Context.root(), "pay-1001", "card", "m-12", 42.0, "approved");
        scoring.recordWarmup("card", 900.0);
        SpanContext optedOutParent = SpanContext.createFromRemoteParent(
                "4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", TraceFlags.getDefault(),
                TraceState.getDefault());
        SpanContext unsampled = scoring.score(Context.root().with(Span.wrap(optedOutParent)),
                "pay-1002", "wallet", "m-12", 8.0, "approved");
        assertThat(unsampled.isSampled()).isFalse();
        assertThat(meterProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
        assertThat(tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();

        Map<String, Metric> metrics = metricsByName(takeMetrics());
        Map<String, HistogramDataPoint> durations = metrics.get("fraud.score.duration").getHistogram()
                .getDataPointsList().stream()
                .collect(Collectors.toMap(point -> attributes(point.getAttributesList()).get(ROUTE.getKey()),
                        Function.identity()));
        HistogramDataPoint card = durations.get("card");
        assertThat(card.getCount()).isEqualTo(2);
        assertThat(card.getSum()).isEqualTo(942.0);
        assertThat(card.getExemplarsList()).singleElement().satisfies(exemplar -> {
            assertThat(exemplar.getAsDouble()).isEqualTo(42.0);
            assertThat(exemplar.getTraceId()).isEqualTo(ByteString.copyFrom(scored.getTraceIdBytes()));
            assertThat(exemplar.getSpanId()).isEqualTo(ByteString.copyFrom(scored.getSpanIdBytes()));
            assertThat(exemplar.getTimeUnixNano()).isPositive();
        });
        HistogramDataPoint wallet = durations.get("wallet");
        assertThat(wallet.getCount()).isEqualTo(1);
        assertThat(wallet.getExemplarsList()).isEmpty();

        ExportTraceServiceRequest traces = takeTraces();
        List<io.opentelemetry.proto.trace.v1.Span> spans = spans(traces);
        assertThat(spans).singleElement().satisfies(span -> {
            assertThat(span.getName()).isEqualTo("score-payment");
            assertThat(span.getTraceId()).isEqualTo(card.getExemplars(0).getTraceId());
            assertThat(span.getSpanId()).isEqualTo(card.getExemplars(0).getSpanId());
        });
        assertThat(traceCollector.getRequestCount()).isEqualTo(1);

        assertThat(meterProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
        assertThat(tracerProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void keepsSpanExemplarsOnTheDecisionPointsAViewAggregates() throws Exception {
        acceptMetrics();
        for (int i = 0; i < 2; i++) {
            acceptTraces();
        }
        SdkTracerProvider tracerProvider = tracerProvider(SpanLimits.getDefault());
        SdkMeterProvider meterProvider = meterProvider(SdkMeterProvider.builder()
                .registerView(
                        InstrumentSelector.builder().setName("fraud.decisions").build(),
                        View.builder()
                                .setDescription("Decisions per outcome across merchants")
                                .setAttributeFilter(DECISION.getKey()::equals)
                                .build()));
        FraudScoringService scoring = new FraudScoringService(
                tracerProvider.get("fraud-scoring"), meterProvider.get("fraud-scoring"));

        SpanContext declined = scoring.score(Context.root(), "pay-2001", "card", "m-77", 120.0, "declined");
        SpanContext approved = scoring.score(Context.root(), "pay-2002", "card", "m-31", 15.0, "approved");
        assertThat(meterProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();

        Metric decisions = metricsByName(takeMetrics()).get("fraud.decisions");
        assertThat(decisions.getDescription()).isEqualTo("Decisions per outcome across merchants");
        Map<String, NumberDataPoint> byDecision = decisions.getSum().getDataPointsList().stream()
                .collect(Collectors.toMap(point -> attributes(point.getAttributesList()).get(DECISION.getKey()),
                        Function.identity()));
        assertThat(byDecision).containsOnlyKeys("declined", "approved");
        assertDecisionExemplar(byDecision.get("declined"), declined);
        assertDecisionExemplar(byDecision.get("approved"), approved);

        assertThat(meterProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
        assertThat(tracerProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void linksTheSettlementBatchToThePaymentTracesWithinSpanLimits() throws Exception {
        acceptTraces();
        SdkTracerProvider tracerProvider = tracerProvider(SpanLimits.builder()
                .setMaxNumberOfLinks(2)
                .setMaxNumberOfAttributesPerLink(1)
                .build());
        SettlementJob job = new SettlementJob(tracerProvider.get("settlement-job"));
        List<SpanContext> payments = List.of(
                payment("0af7651916cd43dd8448eb211c80319c", "b7ad6b7169203331", "batch-7"),
                payment("5b8aa5a2d2c872e8321cf37308d69df2", "051581bf3cb55c13", "batch-7"),
                payment("8a3c60f7d188f8fa79d48a391a778fa6", "53995c3f42cd8ad8", "batch-8"));

        SpanContext batch = job.settle(payments);
        assertThat(tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();

        ExportTraceServiceRequest traces = takeTraces();
        assertThat(attributes(traces.getResourceSpans(0).getResource().getAttributesList()))
                .containsEntry(ResourceAttributes.SERVICE_NAME.getKey(), "fraud-scoring");
        assertThat(spans(traces)).singleElement().satisfies(span -> {
            assertThat(span.getName()).isEqualTo("settle-batch");
            assertThat(span.getKind()).isEqualTo(io.opentelemetry.proto.trace.v1.Span.SpanKind.SPAN_KIND_CONSUMER);
            assertThat(span.getTraceId()).isEqualTo(ByteString.copyFrom(batch.getTraceIdBytes()));
            assertThat(span.getLinksCount()).isEqualTo(2);
            assertThat(span.getDroppedLinksCount()).isEqualTo(1);
            for (int i = 0; i < 2; i++) {
                io.opentelemetry.proto.trace.v1.Span.Link link = span.getLinks(i);
                SpanContext payment = payments.get(i);
                assertThat(link.getTraceId()).isEqualTo(ByteString.copyFrom(payment.getTraceIdBytes()));
                assertThat(link.getSpanId()).isEqualTo(ByteString.copyFrom(payment.getSpanIdBytes()));
                assertThat(link.getTraceState()).isEqualTo("acme=batch-7");
                assertThat(link.getAttributesCount()).isEqualTo(1);
                assertThat(link.getDroppedAttributesCount()).isEqualTo(1);
            }
        });

        assertThat(tracerProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    private static void assertDecisionExemplar(NumberDataPoint point, SpanContext span) {
        assertThat(point.getAsInt()).isEqualTo(1);
        assertThat(attributes(point.getAttributesList())).containsOnlyKeys(DECISION.getKey());
        assertThat(point.getExemplarsList()).singleElement().satisfies(exemplar -> {
            assertThat(exemplar.getValueCase()).isEqualTo(Exemplar.ValueCase.AS_INT);
            assertThat(exemplar.getAsInt()).isEqualTo(1);
            assertThat(exemplar.getSpanId()).isEqualTo(ByteString.copyFrom(span.getSpanIdBytes()));
            assertThat(exemplar.getTraceId()).isEqualTo(ByteString.copyFrom(span.getTraceIdBytes()));
            assertThat(exemplar.getFilteredAttributesList()).isEmpty();
        });
    }

    private static SpanContext payment(String traceId, String spanId, String batch) {
        return SpanContext.createFromRemoteParent(traceId, spanId, TraceFlags.getSampled(),
                TraceState.builder().put("acme", batch).build());
    }

    private SdkTracerProvider tracerProvider(SpanLimits limits) {
        return SdkTracerProvider.builder()
                .setResource(RESOURCE)
                .setSpanLimits(limits)
                .addSpanProcessor(SimpleSpanProcessor.create(OtlpHttpSpanExporter.builder()
                        .setEndpoint(traceCollector.url("/v1/traces").toString())
                        .setTimeout(Duration.ofSeconds(10))
                        .build()))
                .build();
    }

    private SdkMeterProvider meterProvider(SdkMeterProviderBuilder builder) {
        return builder
                .setResource(RESOURCE)
                .registerMetricReader(PeriodicMetricReader.builder(OtlpHttpMetricExporter.builder()
                                .setEndpoint(metricsCollector.url("/v1/metrics").toString())
                                .setTimeout(Duration.ofSeconds(10))
                                .build())
                        .setInterval(Duration.ofMinutes(10))
                        .build())
                .build();
    }

    private void acceptMetrics() {
        metricsCollector.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/x-protobuf")
                .setBody(new Buffer().write(ExportMetricsServiceResponse.getDefaultInstance().toByteArray())));
    }

    private void acceptTraces() {
        traceCollector.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/x-protobuf")
                .setBody(new Buffer().write(ExportTraceServiceResponse.getDefaultInstance().toByteArray())));
    }

    private ExportMetricsServiceRequest takeMetrics() throws Exception {
        RecordedRequest request = metricsCollector.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/v1/metrics");
        return ExportMetricsServiceRequest.parseFrom(request.getBody().readByteArray());
    }

    private ExportTraceServiceRequest takeTraces() throws Exception {
        RecordedRequest request = traceCollector.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/v1/traces");
        return ExportTraceServiceRequest.parseFrom(request.getBody().readByteArray());
    }

    private static Map<String, Metric> metricsByName(ExportMetricsServiceRequest export) {
        return export.getResourceMetricsList().stream()
                .flatMap(resourceMetrics -> resourceMetrics.getScopeMetricsList().stream())
                .flatMap(scopeMetrics -> scopeMetrics.getMetricsList().stream())
                .collect(Collectors.toMap(Metric::getName, Function.identity()));
    }

    private static List<io.opentelemetry.proto.trace.v1.Span> spans(ExportTraceServiceRequest export) {
        return export.getResourceSpansList().stream()
                .flatMap(resourceSpans -> resourceSpans.getScopeSpansList().stream())
                .flatMap(scopeSpans -> scopeSpans.getSpansList().stream())
                .collect(Collectors.toList());
    }

    private static Map<String, String> attributes(List<KeyValue> keyValues) {
        return keyValues.stream()
                .collect(Collectors.toMap(KeyValue::getKey, keyValue -> keyValue.getValue().getStringValue()));
    }

    /// The application code under measurement: scores a payment inside its own span.
    private static final class FraudScoringService {

        private final Tracer tracer;

        private final DoubleHistogram scoreDuration;

        private final LongCounter decisions;

        FraudScoringService(Tracer tracer, Meter meter) {
            this.tracer = tracer;
            scoreDuration = meter.histogramBuilder("fraud.score.duration")
                    .setDescription("Time spent scoring a payment")
                    .setUnit("ms")
                    .build();
            decisions = meter.counterBuilder("fraud.decisions")
                    .setDescription("Scoring decisions")
                    .setUnit("{decision}")
                    .build();
        }

        SpanContext score(Context parent, String paymentId, String route, String merchant, double millis,
                String decision) {
            Span span = tracer.spanBuilder("score-payment")
                    .setParent(parent)
                    .setSpanKind(SpanKind.SERVER)
                    .setAttribute(PAYMENT_ID, paymentId)
                    .startSpan();
            try (Scope ignored = span.makeCurrent()) {
                scoreDuration.record(millis, Attributes.of(ROUTE, route));
                decisions.add(1, Attributes.of(DECISION, decision, MERCHANT, merchant));
            } finally {
                span.end();
            }
            return span.getSpanContext();
        }

        /// Model warm-up runs outside any request, so it has no span to point an exemplar at.
        void recordWarmup(String route, double millis) {
            scoreDuration.record(millis, Attributes.of(ROUTE, route));
        }
    }

    /// The nightly job that settles a batch of payments scored in earlier, separate traces.
    private static final class SettlementJob {

        private final Tracer tracer;

        SettlementJob(Tracer tracer) {
            this.tracer = tracer;
        }

        SpanContext settle(List<SpanContext> payments) {
            SpanBuilder builder = tracer.spanBuilder("settle-batch")
                    .setNoParent()
                    .setSpanKind(SpanKind.CONSUMER);
            for (int i = 0; i < payments.size(); i++) {
                builder.addLink(payments.get(i), Attributes.of(
                        PAYMENT_ID, "pay-" + (3001 + i), SETTLEMENT_REGION, "eu-west"));
            }
            Span span = builder.startSpan();
            span.end();
            return span.getSpanContext();
        }
    }
}
