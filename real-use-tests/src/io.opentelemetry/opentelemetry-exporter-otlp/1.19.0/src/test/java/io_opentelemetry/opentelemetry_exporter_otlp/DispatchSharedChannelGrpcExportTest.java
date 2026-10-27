/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_exporter_otlp;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Span and metric export over OTLP/gRPC through an application-managed grpc-java channel.
 *
 * A ride dispatch service already talks gRPC to its backends, so it builds one `ManagedChannel`
 * with its own tenant interceptor and hands that channel to both `OtlpGrpcSpanExporter` and
 * `OtlpGrpcMetricExporter` through `setChannel`, instead of letting each exporter open its own
 * connection. The collector here is an in-process gRPC server implementing the OTLP trace and
 * metrics services, so the test asserts on what a real collector receives over the shared
 * channel, on the headers the interceptor and the exporters attach, and on export deadlines.
 */
public class DispatchSharedChannelGrpcExportTest {

    private static final long WAIT_SECONDS = 20;

    private static final Metadata.Key<String> TENANT =
            Metadata.Key.of("x-dispatch-tenant", Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> API_KEY =
            Metadata.Key.of("x-collector-api-key", Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> GRPC_ENCODING =
            Metadata.Key.of("grpc-encoding", Metadata.ASCII_STRING_MARSHALLER);

    private static final AttributeKey<String> CITY = AttributeKey.stringKey("dispatch.city");

    private static final AttributeKey<String> RIDE_ID = AttributeKey.stringKey("dispatch.ride_id");

    private TraceCollector traceCollector;

    private MetricsCollector metricsCollector;

    private List<CollectorCall> calls;

    private Server server;

    private ManagedChannel channel;

    @BeforeEach
    void startCollectorAndChannel() throws IOException {
        calls = new CopyOnWriteArrayList<>();
        traceCollector = new TraceCollector();
        metricsCollector = new MetricsCollector();
        ServerInterceptor callRecorder = new CallRecorder(calls);
        server = NettyServerBuilder.forPort(0)
                .addService(ServerInterceptors.intercept(traceCollector, callRecorder))
                .addService(ServerInterceptors.intercept(metricsCollector, callRecorder))
                .build()
                .start();
        channel = NettyChannelBuilder.forAddress("localhost", server.getPort())
                .usePlaintext()
                .intercept(new TenantInterceptor("eu-ride-hailing"))
                .build();
    }

    @AfterEach
    void stopCollectorAndChannel() throws InterruptedException {
        channel.shutdownNow();
        channel.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
        server.shutdownNow();
        server.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void shipsSpansAndMetricsOverOneSharedChannel() {
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setChannel(channel)
                .setTimeout(Duration.ofSeconds(10))
                .addHeader(API_KEY.name(), "dispatch-traces")
                .build();
        OtlpGrpcMetricExporter metricExporter = OtlpGrpcMetricExporter.builder()
                .setChannel(channel)
                .setTimeout(Duration.ofSeconds(10))
                .addHeader(API_KEY.name(), "dispatch-metrics")
                .build();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(dispatchResource())
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .setResource(dispatchResource())
                .registerMetricReader(PeriodicMetricReader.builder(metricExporter)
                        .setInterval(Duration.ofMinutes(10))
                        .build())
                .build();
        AtomicInteger idleDrivers = new AtomicInteger(42);
        DispatchService dispatch = new DispatchService(
                tracerProvider.get("dispatch-service"), meterProvider.get("dispatch-service"), idleDrivers);

        dispatch.assignRide("ride-501", "berlin");
        dispatch.assignRide("ride-502", "berlin");
        dispatch.assignRide("ride-503", "lisbon");
        idleDrivers.set(39);

        assertThat(tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
        assertThat(meterProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();

        List<io.opentelemetry.proto.trace.v1.Span> spans = traceCollector.receivedSpans();
        assertThat(spans).extracting(io.opentelemetry.proto.trace.v1.Span::getName)
                .containsOnly("assign-ride")
                .hasSize(3);
        assertThat(spans).extracting(span -> attributes(span.getAttributesList()).get(RIDE_ID.getKey()))
                .containsExactlyInAnyOrder("ride-501", "ride-502", "ride-503");
        assertThat(attributes(traceCollector.requests.get(0).getResourceSpans(0).getResource().getAttributesList()))
                .containsEntry(ResourceAttributes.SERVICE_NAME.getKey(), "ride-dispatch");

        Map<String, Metric> metrics = metricsCollector.receivedMetrics();
        assertThat(metrics).containsKeys("dispatch.rides_assigned", "dispatch.idle_drivers");
        Metric assigned = metrics.get("dispatch.rides_assigned");
        assertThat(assigned.getUnit()).isEqualTo("{ride}");
        assertThat(assigned.getSum().getIsMonotonic()).isTrue();
        Map<String, Long> ridesByCity = assigned.getSum().getDataPointsList().stream().collect(Collectors.toMap(
                point -> attributes(point.getAttributesList()).get(CITY.getKey()), NumberDataPoint::getAsInt));
        assertThat(ridesByCity).containsOnly(Map.entry("berlin", 2L), Map.entry("lisbon", 1L));
        assertThat(metrics.get("dispatch.idle_drivers").getGauge().getDataPointsList())
                .extracting(NumberDataPoint::getAsInt)
                .containsExactly(39L);
        assertThat(attributes(metricsCollector.requests.get(0).getResourceMetrics(0).getResource()
                .getAttributesList()))
                .containsEntry(ResourceAttributes.SERVICE_NAME.getKey(), "ride-dispatch");

        assertThat(calls).extracting(call -> call.method)
                .contains(
                        "opentelemetry.proto.collector.trace.v1.TraceService/Export",
                        "opentelemetry.proto.collector.metrics.v1.MetricsService/Export");
        assertThat(calls).allSatisfy(call -> assertThat(call.headers.get(TENANT)).isEqualTo("eu-ride-hailing"));
        assertThat(calls).filteredOn(call -> call.method.contains("TraceService"))
                .allSatisfy(call -> assertThat(call.headers.get(API_KEY)).isEqualTo("dispatch-traces"));
        assertThat(calls).filteredOn(call -> call.method.contains("MetricsService"))
                .allSatisfy(call -> assertThat(call.headers.get(API_KEY)).isEqualTo("dispatch-metrics"));

        assertThat(tracerProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
        assertThat(meterProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void compressesExportsOnTheSharedChannelWithGzip() {
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setChannel(channel)
                .setTimeout(10, TimeUnit.SECONDS)
                .setCompression("gzip")
                .build();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(dispatchResource())
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        DispatchService dispatch = new DispatchService(
                tracerProvider.get("dispatch-service"), null, new AtomicInteger());

        dispatch.assignRide("ride-601", "madrid");

        assertThat(tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
        assertThat(calls).isNotEmpty().allSatisfy(call -> {
            assertThat(call.headers.get(GRPC_ENCODING)).isEqualTo("gzip");
            assertThat(call.headers.get(TENANT)).isEqualTo("eu-ride-hailing");
        });
        assertThat(traceCollector.receivedSpans())
                .extracting(span -> attributes(span.getAttributesList()).get(CITY.getKey()))
                .containsExactly("madrid");
        assertThat(tracerProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void failsAnExportThatOutlivesItsDeadline() throws InterruptedException {
        ExportOutcomes outcomes = new ExportOutcomes(OtlpGrpcSpanExporter.builder()
                .setChannel(channel)
                .setTimeout(Duration.ofMillis(300))
                .build());
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(dispatchResource())
                .addSpanProcessor(SimpleSpanProcessor.create(outcomes))
                .build();
        DispatchService dispatch = new DispatchService(
                tracerProvider.get("dispatch-service"), null, new AtomicInteger());

        CountDownLatch release = traceCollector.stallNext();
        dispatch.assignRide("ride-701", "porto");
        tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS);
        release.countDown();

        dispatch.assignRide("ride-702", "porto");
        tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS);

        assertThat(outcomes.results).containsExactly(false, true);
        assertThat(traceCollector.receivedSpans())
                .extracting(span -> attributes(span.getAttributesList()).get(RIDE_ID.getKey()))
                .contains("ride-702");
        assertThat(tracerProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    private static Resource dispatchResource() {
        return Resource.getDefault().merge(Resource.create(Attributes.of(
                ResourceAttributes.SERVICE_NAME, "ride-dispatch",
                ResourceAttributes.SERVICE_VERSION, "4.2.0")));
    }

    /// Flattens OTLP attributes to strings, the way a collector UI would show them.
    private static Map<String, String> attributes(List<KeyValue> keyValues) {
        return keyValues.stream().collect(Collectors.toMap(KeyValue::getKey, keyValue ->
                keyValue.getValue().hasStringValue()
                        ? keyValue.getValue().getStringValue()
                        : keyValue.getValue().toString().trim()));
    }

    /// The application code under observation: one span and one counted ride per assignment.
    private static final class DispatchService {

        private final Tracer tracer;

        private final LongCounter ridesAssigned;

        DispatchService(Tracer tracer, Meter meter, AtomicInteger idleDrivers) {
            this.tracer = tracer;
            if (meter == null) {
                this.ridesAssigned = null;
                return;
            }
            this.ridesAssigned = meter.counterBuilder("dispatch.rides_assigned")
                    .setDescription("Rides matched to a driver")
                    .setUnit("{ride}")
                    .build();
            meter.gaugeBuilder("dispatch.idle_drivers")
                    .setDescription("Drivers waiting for a ride")
                    .ofLongs()
                    .buildWithCallback(measurement -> measurement.record(idleDrivers.get()));
        }

        void assignRide(String rideId, String city) {
            Span span = tracer.spanBuilder("assign-ride")
                    .setSpanKind(SpanKind.INTERNAL)
                    .setAttribute(RIDE_ID, rideId)
                    .setAttribute(CITY, city)
                    .startSpan();
            try (Scope ignored = span.makeCurrent()) {
                if (ridesAssigned != null) {
                    ridesAssigned.add(1, Attributes.of(CITY, city));
                }
            } finally {
                span.end();
            }
        }
    }

    /// The dispatch service's own client interceptor, stamping its tenant on every outgoing call.
    private static final class TenantInterceptor implements ClientInterceptor {

        private final String tenant;

        TenantInterceptor(String tenant) {
            this.tenant = tenant;
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
            return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                    next.newCall(method, callOptions)) {
                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    headers.put(TENANT, tenant);
                    super.start(responseListener, headers);
                }
            };
        }
    }

    /// A span exporter decorator that records each export outcome for the service's health page.
    private static final class ExportOutcomes implements SpanExporter {

        private final SpanExporter delegate;

        private final List<Boolean> results = new CopyOnWriteArrayList<>();

        ExportOutcomes(SpanExporter delegate) {
            this.delegate = delegate;
        }

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
    }

    /// One call the collector received: the full method name and the request headers.
    private static final class CollectorCall {

        private final String method;

        private final Metadata headers;

        CollectorCall(String method, Metadata headers) {
            this.method = method;
            this.headers = headers;
        }
    }

    /// Records every call the collector receives, whichever OTLP service it targets.
    private static final class CallRecorder implements ServerInterceptor {

        private final List<CollectorCall> calls;

        CallRecorder(List<CollectorCall> calls) {
            this.calls = calls;
        }

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call, Metadata metadata, ServerCallHandler<ReqT, RespT> next) {
            calls.add(new CollectorCall(call.getMethodDescriptor().getFullMethodName(), metadata));
            return next.startCall(call, metadata);
        }
    }

    /// An in-process OTLP trace collector that can stall one export to simulate a slow backend.
    private static final class TraceCollector extends TraceServiceGrpc.TraceServiceImplBase {

        private final List<ExportTraceServiceRequest> requests = new CopyOnWriteArrayList<>();

        private volatile CountDownLatch stall;

        CountDownLatch stallNext() {
            CountDownLatch latch = new CountDownLatch(1);
            stall = latch;
            return latch;
        }

        List<io.opentelemetry.proto.trace.v1.Span> receivedSpans() {
            List<io.opentelemetry.proto.trace.v1.Span> spans = new ArrayList<>();
            requests.forEach(request -> request.getResourceSpansList().forEach(resourceSpans ->
                    resourceSpans.getScopeSpansList().forEach(scopeSpans ->
                            spans.addAll(scopeSpans.getSpansList()))));
            return spans;
        }

        @Override
        public void export(
                ExportTraceServiceRequest request, StreamObserver<ExportTraceServiceResponse> responseObserver) {
            CountDownLatch latch = stall;
            stall = null;
            if (latch != null) {
                try {
                    latch.await(WAIT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            requests.add(request);
            responseObserver.onNext(ExportTraceServiceResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }
    }

    /// An in-process OTLP metrics collector that records the requests it receives.
    private static final class MetricsCollector extends MetricsServiceGrpc.MetricsServiceImplBase {

        private final List<ExportMetricsServiceRequest> requests = new CopyOnWriteArrayList<>();

        Map<String, Metric> receivedMetrics() {
            List<Metric> metrics = new ArrayList<>();
            requests.forEach(request -> request.getResourceMetricsList().forEach(resourceMetrics ->
                    resourceMetrics.getScopeMetricsList().forEach(scopeMetrics ->
                            metrics.addAll(scopeMetrics.getMetricsList()))));
            return metrics.stream().collect(Collectors.toMap(Metric::getName, metric -> metric));
        }

        @Override
        public void export(
                ExportMetricsServiceRequest request, StreamObserver<ExportMetricsServiceResponse> responseObserver) {
            requests.add(request);
            responseObserver.onNext(ExportMetricsServiceResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }
    }
}
