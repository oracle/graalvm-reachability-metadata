/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_exporter_otlp;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Server;
import io.grpc.Status;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exporter self-monitoring through `setMeterProvider` on the OTLP span exporter builders.
 *
 * A billing service wants to know when its traces stop reaching the collector, so it hands each
 * OTLP span exporter a separate monitoring `SdkMeterProvider`. The exporters then count every
 * span they are given (`otlp.exporter.seen`) and every span they delivered or dropped
 * (`otlp.exporter.exported` with `success=true|false`), under one meter per transport. A
 * health dashboard reads those counters from the monitoring provider. The collectors here are a
 * MockWebServer for OTLP/HTTP and an in-process gRPC trace service for OTLP/gRPC, so the test
 * asserts on the delivery health an operator would see while a collector accepts, rejects, or is
 * unreachable.
 */
public class BillingExporterHealthMetricsTest {

    private static final long WAIT_SECONDS = 20;

    private static final String SEEN = "otlp.exporter.seen";

    private static final String EXPORTED = "otlp.exporter.exported";

    private static final String HTTP_SCOPE = "io.opentelemetry.exporters.otlp-http";

    private static final String GRPC_SCOPE = "io.opentelemetry.exporters.otlp-grpc-okhttp";

    private static final AttributeKey<String> TYPE = AttributeKey.stringKey("type");

    private static final AttributeKey<Boolean> SUCCESS = AttributeKey.booleanKey("success");

    private static final AttributeKey<String> INVOICE_ID = AttributeKey.stringKey("billing.invoice_id");

    private static final AttributeKey<String> CURRENCY = AttributeKey.stringKey("billing.currency");

    private MockWebServer httpCollector;

    private HealthDashboard dashboard;

    private SdkMeterProvider monitoring;

    @BeforeEach
    void startHttpCollectorAndMonitoring() throws IOException {
        httpCollector = new MockWebServer();
        httpCollector.start();
        dashboard = new HealthDashboard();
        monitoring = SdkMeterProvider.builder()
                .setResource(billingResource())
                .registerMetricReader(PeriodicMetricReader.builder(dashboard)
                        .setInterval(Duration.ofMinutes(10))
                        .build())
                .build();
    }

    @AfterEach
    void stopHttpCollectorAndMonitoring() throws IOException {
        monitoring.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS);
        httpCollector.shutdown();
    }

    @Test
    void countsDeliveredAndRejectedSpansOverOtlpHttp() throws Exception {
        acceptHttpExport();
        acceptHttpExport();
        httpCollector.enqueue(new MockResponse().setResponseCode(400).setBody("invalid tenant"));
        OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(httpCollector.url("/v1/traces").toString())
                .setTimeout(Duration.ofSeconds(10))
                .setMeterProvider(monitoring)
                .build();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(billingResource())
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        BillingService billing = new BillingService(tracerProvider.get("billing-service"));

        billing.issueInvoice("inv-100", "EUR");
        billing.issueInvoice("inv-101", "USD");
        assertThat(tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
        billing.issueInvoice("inv-102", "EUR");
        tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS);

        assertThat(httpCollector.getRequestCount()).isEqualTo(3);
        Health health = dashboard.refresh(monitoring);
        assertThat(health.count(HTTP_SCOPE, SEEN, Attributes.of(TYPE, "span"))).isEqualTo(3);
        assertThat(health.count(HTTP_SCOPE, EXPORTED, Attributes.of(TYPE, "span", SUCCESS, true))).isEqualTo(2);
        assertThat(health.count(HTTP_SCOPE, EXPORTED, Attributes.of(TYPE, "span", SUCCESS, false))).isEqualTo(1);
        assertThat(health.scopes()).containsOnly(HTTP_SCOPE);
        assertThat(health.isMonotonicCumulativeSum(HTTP_SCOPE, SEEN)).isTrue();

        assertThat(tracerProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void countsWholeBatchesAcceptedOrRejectedOverOtlpGrpc() throws Exception {
        TraceCollector traceCollector = new TraceCollector();
        Server server = NettyServerBuilder.forPort(0).addService(traceCollector).build().start();
        try {
            OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                    .setEndpoint("http://localhost:" + server.getPort())
                    .setTimeout(Duration.ofSeconds(10))
                    .setMeterProvider(monitoring)
                    .build();
            SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                    .setResource(billingResource())
                    .addSpanProcessor(BatchSpanProcessor.builder(exporter)
                            .setScheduleDelay(Duration.ofSeconds(30))
                            .build())
                    .build();
            BillingService billing = new BillingService(tracerProvider.get("billing-service"));

            traceCollector.rejectNext(Status.INVALID_ARGUMENT.withDescription("payload too large"));
            billing.issueInvoice("inv-200", "EUR");
            billing.issueInvoice("inv-201", "EUR");
            billing.issueInvoice("inv-202", "GBP");
            tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS);
            billing.issueInvoice("inv-203", "USD");
            billing.issueInvoice("inv-204", "USD");
            tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS);

            assertThat(traceCollector.receivedInvoices()).containsExactlyInAnyOrder("inv-203", "inv-204");
            Health health = dashboard.refresh(monitoring);
            assertThat(health.count(GRPC_SCOPE, SEEN, Attributes.of(TYPE, "span"))).isEqualTo(5);
            assertThat(health.count(GRPC_SCOPE, EXPORTED, Attributes.of(TYPE, "span", SUCCESS, true)))
                    .isEqualTo(2);
            assertThat(health.count(GRPC_SCOPE, EXPORTED, Attributes.of(TYPE, "span", SUCCESS, false)))
                    .isEqualTo(3);
            assertThat(health.scopes()).containsOnly(GRPC_SCOPE);

            assertThat(tracerProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
        } finally {
            server.shutdownNow();
            server.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void separatesDropsPerTransportWhileTheCollectorIsUnreachable() throws Exception {
        MockWebServer stopped = new MockWebServer();
        stopped.start();
        String unreachable = "http://localhost:" + stopped.getPort();
        stopped.shutdown();

        OtlpHttpSpanExporter httpExporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(unreachable + "/v1/traces")
                .setTimeout(Duration.ofSeconds(5))
                .setMeterProvider(monitoring)
                .build();
        OtlpGrpcSpanExporter grpcExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(unreachable)
                .setTimeout(5, TimeUnit.SECONDS)
                .setMeterProvider(monitoring)
                .build();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(billingResource())
                .addSpanProcessor(SimpleSpanProcessor.create(httpExporter))
                .addSpanProcessor(SimpleSpanProcessor.create(grpcExporter))
                .build();
        BillingService billing = new BillingService(tracerProvider.get("billing-service"));

        billing.issueInvoice("inv-300", "EUR");
        billing.issueInvoice("inv-301", "CHF");
        tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS);

        Health health = dashboard.refresh(monitoring);
        assertThat(health.scopes()).containsOnly(HTTP_SCOPE, GRPC_SCOPE);
        for (String scope : List.of(HTTP_SCOPE, GRPC_SCOPE)) {
            assertThat(health.count(scope, SEEN, Attributes.of(TYPE, "span"))).isEqualTo(2);
            assertThat(health.count(scope, EXPORTED, Attributes.of(TYPE, "span", SUCCESS, false))).isEqualTo(2);
            assertThat(health.count(scope, EXPORTED, Attributes.of(TYPE, "span", SUCCESS, true))).isZero();
        }

        tracerProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    private void acceptHttpExport() {
        httpCollector.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/x-protobuf")
                .setBody(new Buffer().write(ExportTraceServiceResponse.getDefaultInstance().toByteArray())));
    }

    private static Resource billingResource() {
        return Resource.getDefault().merge(Resource.create(Attributes.of(
                ResourceAttributes.SERVICE_NAME, "billing")));
    }

    /// The application code under observation: one span per issued invoice.
    private static final class BillingService {

        private final Tracer tracer;

        BillingService(Tracer tracer) {
            this.tracer = tracer;
        }

        void issueInvoice(String invoiceId, String currency) {
            tracer.spanBuilder("issue-invoice")
                    .setSpanKind(SpanKind.INTERNAL)
                    .setAttribute(INVOICE_ID, invoiceId)
                    .setAttribute(CURRENCY, currency)
                    .startSpan()
                    .end();
        }
    }

    /// The exporter health counters as of one collection of the monitoring provider.
    private static final class Health {

        private final List<MetricData> metrics;

        Health(List<MetricData> metrics) {
            this.metrics = metrics;
        }

        List<String> scopes() {
            List<String> scopes = new ArrayList<>();
            metrics.forEach(metric -> scopes.add(metric.getInstrumentationScopeInfo().getName()));
            return scopes;
        }

        boolean isMonotonicCumulativeSum(String scope, String name) {
            MetricData metric = metric(scope, name);
            return metric.getLongSumData().isMonotonic()
                    && metric.getLongSumData().getAggregationTemporality() == AggregationTemporality.CUMULATIVE;
        }

        long count(String scope, String name, Attributes attributes) {
            return metric(scope, name).getLongSumData().getPoints().stream()
                    .filter(point -> point.getAttributes().equals(attributes))
                    .mapToLong(LongPointData::getValue)
                    .sum();
        }

        private MetricData metric(String scope, String name) {
            return metrics.stream()
                    .filter(metric -> metric.getInstrumentationScopeInfo().getName().equals(scope))
                    .filter(metric -> metric.getName().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No " + name + " in " + scope + ": " + metrics));
        }
    }

    /// The operator's health dashboard: keeps the latest collection of the monitoring provider.
    private static final class HealthDashboard implements MetricExporter {

        private final AtomicReference<List<MetricData>> latest = new AtomicReference<>(List.of());

        Health refresh(SdkMeterProvider monitoring) {
            assertThat(monitoring.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
            return new Health(latest.get());
        }

        @Override
        public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
            return AggregationTemporality.CUMULATIVE;
        }

        @Override
        public CompletableResultCode export(Collection<MetricData> metrics) {
            latest.set(new ArrayList<>(metrics));
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }

    /// An in-process OTLP trace collector that can reject the next batch it receives.
    private static final class TraceCollector extends TraceServiceGrpc.TraceServiceImplBase {

        private final List<ExportTraceServiceRequest> requests = new CopyOnWriteArrayList<>();

        private final AtomicReference<Status> pendingRejection = new AtomicReference<>();

        void rejectNext(Status status) {
            pendingRejection.set(status);
        }

        List<String> receivedInvoices() {
            List<String> invoiceIds = new ArrayList<>();
            requests.forEach(request -> request.getResourceSpansList().forEach(resourceSpans ->
                    resourceSpans.getScopeSpansList().forEach(scopeSpans ->
                            scopeSpans.getSpansList().forEach(span -> span.getAttributesList().stream()
                                    .filter(attribute -> attribute.getKey().equals(INVOICE_ID.getKey()))
                                    .forEach(attribute -> invoiceIds.add(attribute.getValue().getStringValue()))))));
            return invoiceIds;
        }

        @Override
        public void export(
                ExportTraceServiceRequest request, StreamObserver<ExportTraceServiceResponse> responseObserver) {
            Status rejection = pendingRejection.getAndSet(null);
            if (rejection != null) {
                responseObserver.onError(rejection.asRuntimeException());
                return;
            }
            requests.add(request);
            responseObserver.onNext(ExportTraceServiceResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }
    }
}
