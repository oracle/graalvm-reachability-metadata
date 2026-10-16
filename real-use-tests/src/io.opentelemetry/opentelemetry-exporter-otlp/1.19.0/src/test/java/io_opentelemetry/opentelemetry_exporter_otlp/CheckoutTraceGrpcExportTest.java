/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_exporter_otlp;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Span export over OTLP/gRPC with `OtlpGrpcSpanExporter` on its default transport.
 *
 * A checkout service traces each order: a server span for the HTTP request and client spans for
 * the payment call. The tracer provider batches the spans and ships them over OTLP/gRPC to a
 * collector, with an API key header and optional gzip compression. The collector here is an
 * in-process gRPC server implementing the OTLP trace service, so the test asserts on what a
 * real collector would receive and on how the service observes a collector that rejects a batch.
 */
public class CheckoutTraceGrpcExportTest {

    private static final long WAIT_SECONDS = 20;

    private static final Metadata.Key<String> API_KEY =
            Metadata.Key.of("x-collector-api-key", Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> GRPC_ENCODING =
            Metadata.Key.of("grpc-encoding", Metadata.ASCII_STRING_MARSHALLER);

    private static final AttributeKey<String> ORDER_ID = AttributeKey.stringKey("checkout.order_id");

    private static final AttributeKey<Long> ITEM_COUNT = AttributeKey.longKey("checkout.item_count");

    private Collector collector;

    private Server server;

    @BeforeEach
    void startCollector() throws IOException {
        collector = new Collector();
        server = NettyServerBuilder.forPort(0)
                .addService(ServerInterceptors.intercept(collector, collector.headerRecorder()))
                .build()
                .start();
    }

    @AfterEach
    void stopCollector() throws InterruptedException {
        server.shutdownNow();
        server.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void shipsABatchedCheckoutTraceToTheCollector() {
        OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(collectorEndpoint())
                .setTimeout(Duration.ofSeconds(10))
                .addHeader(API_KEY.name(), "checkout-secret")
                .build();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(checkoutResource())
                .addSpanProcessor(BatchSpanProcessor.builder(exporter)
                        .setScheduleDelay(Duration.ofSeconds(30))
                        .build())
                .build();
        CheckoutService checkout = new CheckoutService(tracerProvider.get("checkout-service"));

        checkout.placeOrder("order-1001", 3, true);
        checkout.placeOrder("order-1002", 1, false);
        CompletableResultCode flushed = tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS);

        assertThat(flushed.isSuccess()).isTrue();
        assertThat(collector.headers).isNotEmpty();
        assertThat(collector.headers.get(0).get(API_KEY)).isEqualTo("checkout-secret");

        List<io.opentelemetry.proto.trace.v1.Span> spans = collector.receivedSpans();
        assertThat(spans).extracting(io.opentelemetry.proto.trace.v1.Span::getName)
                .containsExactlyInAnyOrder("POST /checkout", "charge-card", "POST /checkout", "charge-card");

        io.opentelemetry.proto.resource.v1.Resource resource = collector.requests.get(0)
                .getResourceSpans(0)
                .getResource();
        assertThat(attributes(resource.getAttributesList()))
                .containsEntry(ResourceAttributes.SERVICE_NAME.getKey(), "checkout")
                .containsEntry(ResourceAttributes.DEPLOYMENT_ENVIRONMENT.getKey(), "staging");
        assertThat(collector.requests.get(0).getResourceSpans(0).getScopeSpans(0).getScope().getName())
                .isEqualTo("checkout-service");

        Map<String, io.opentelemetry.proto.trace.v1.Span> serverSpansByOrder = spans.stream()
                .filter(span -> span.getKind() == io.opentelemetry.proto.trace.v1.Span.SpanKind.SPAN_KIND_SERVER)
                .collect(Collectors.toMap(
                        span -> attributes(span.getAttributesList()).get(ORDER_ID.getKey()), Function.identity()));
        assertThat(serverSpansByOrder).containsOnlyKeys("order-1001", "order-1002");

        io.opentelemetry.proto.trace.v1.Span acceptedOrder = serverSpansByOrder.get("order-1001");
        assertThat(attributes(acceptedOrder.getAttributesList()))
                .containsEntry(ITEM_COUNT.getKey(), "3")
                .containsEntry(SemanticAttributes.HTTP_METHOD.getKey(), "POST")
                .containsEntry(SemanticAttributes.HTTP_STATUS_CODE.getKey(), "201");
        assertThat(acceptedOrder.getParentSpanId().isEmpty()).isTrue();
        assertThat(acceptedOrder.getStatus().getCode())
                .isEqualTo(io.opentelemetry.proto.trace.v1.Status.StatusCode.STATUS_CODE_UNSET);

        io.opentelemetry.proto.trace.v1.Span acceptedCharge = childOf(spans, acceptedOrder);
        assertThat(acceptedCharge.getName()).isEqualTo("charge-card");
        assertThat(acceptedCharge.getKind())
                .isEqualTo(io.opentelemetry.proto.trace.v1.Span.SpanKind.SPAN_KIND_CLIENT);
        assertThat(acceptedCharge.getEventsList())
                .extracting(io.opentelemetry.proto.trace.v1.Span.Event::getName)
                .containsExactly("payment.authorized");

        io.opentelemetry.proto.trace.v1.Span declinedOrder = serverSpansByOrder.get("order-1002");
        assertThat(attributes(declinedOrder.getAttributesList()))
                .containsEntry(SemanticAttributes.HTTP_STATUS_CODE.getKey(), "402");
        io.opentelemetry.proto.trace.v1.Span declinedCharge = childOf(spans, declinedOrder);
        assertThat(declinedCharge.getStatus().getCode())
                .isEqualTo(io.opentelemetry.proto.trace.v1.Status.StatusCode.STATUS_CODE_ERROR);
        assertThat(declinedCharge.getStatus().getMessage()).isEqualTo("card declined");
        assertThat(declinedCharge.getEventsList())
                .extracting(io.opentelemetry.proto.trace.v1.Span.Event::getName)
                .containsExactly("exception");
        assertThat(declinedCharge.getTraceId()).isNotEqualTo(acceptedCharge.getTraceId());

        assertThat(tracerProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void compressesExportsWithGzipWhenConfigured() {
        OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(collectorEndpoint())
                .setTimeout(10, TimeUnit.SECONDS)
                .setCompression("gzip")
                .build();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(checkoutResource())
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        CheckoutService checkout = new CheckoutService(tracerProvider.get("checkout-service"));

        checkout.placeOrder("order-2001", 5, true);
        CompletableResultCode flushed = tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS);

        assertThat(flushed.isSuccess()).isTrue();
        assertThat(collector.headers).isNotEmpty().allSatisfy(
                headers -> assertThat(headers.get(GRPC_ENCODING)).isEqualTo("gzip"));
        assertThat(collector.receivedSpans())
                .extracting(io.opentelemetry.proto.trace.v1.Span::getName)
                .containsExactlyInAnyOrder("charge-card", "POST /checkout");
        assertThat(tracerProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void reportsARejectedBatchAndRecoversOnTheNextOne() {
        ExportOutcomes outcomes = new ExportOutcomes(OtlpGrpcSpanExporter.builder()
                .setEndpoint(collectorEndpoint())
                .setTimeout(Duration.ofSeconds(10))
                .build());
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(checkoutResource())
                .addSpanProcessor(BatchSpanProcessor.builder(outcomes)
                        .setScheduleDelay(Duration.ofSeconds(30))
                        .build())
                .build();
        CheckoutService checkout = new CheckoutService(tracerProvider.get("checkout-service"));

        collector.rejectNext(Status.INVALID_ARGUMENT.withDescription("quota exceeded for tenant"));
        checkout.placeOrder("order-3001", 2, true);
        tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS);

        checkout.placeOrder("order-3002", 4, true);
        tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS);

        assertThat(outcomes.results).containsExactly(false, true);
        assertThat(outcomes.exportedSpanCounts).containsExactly(2, 2);
        assertThat(collector.requests).hasSize(1);
        assertThat(collector.receivedSpans())
                .filteredOn(span -> span.getKind() == io.opentelemetry.proto.trace.v1.Span.SpanKind.SPAN_KIND_SERVER)
                .extracting(span -> attributes(span.getAttributesList()).get(ORDER_ID.getKey()))
                .containsExactly("order-3002");
        assertThat(tracerProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    private String collectorEndpoint() {
        return "http://localhost:" + server.getPort();
    }

    private static Resource checkoutResource() {
        return Resource.getDefault().merge(Resource.create(Attributes.of(
                ResourceAttributes.SERVICE_NAME, "checkout",
                ResourceAttributes.DEPLOYMENT_ENVIRONMENT, "staging")));
    }

    private static io.opentelemetry.proto.trace.v1.Span childOf(
            List<io.opentelemetry.proto.trace.v1.Span> spans, io.opentelemetry.proto.trace.v1.Span parent) {
        List<io.opentelemetry.proto.trace.v1.Span> children = spans.stream()
                .filter(span -> span.getParentSpanId().equals(parent.getSpanId()))
                .collect(Collectors.toList());
        assertThat(children).hasSize(1);
        assertThat(children.get(0).getTraceId()).isEqualTo(parent.getTraceId());
        return children.get(0);
    }

    /// Flattens OTLP attributes to strings, the way a collector UI would show them.
    private static Map<String, String> attributes(List<KeyValue> keyValues) {
        return keyValues.stream().collect(Collectors.toMap(KeyValue::getKey, keyValue -> {
            AnyValue value = keyValue.getValue();
            switch (value.getValueCase()) {
                case STRING_VALUE:
                    return value.getStringValue();
                case INT_VALUE:
                    return Long.toString(value.getIntValue());
                case BOOL_VALUE:
                    return Boolean.toString(value.getBoolValue());
                case DOUBLE_VALUE:
                    return Double.toString(value.getDoubleValue());
                default:
                    return value.toString();
            }
        }));
    }

    /// The application code under trace: one server span per order, one client span per charge.
    private static final class CheckoutService {

        private final Tracer tracer;

        CheckoutService(Tracer tracer) {
            this.tracer = tracer;
        }

        void placeOrder(String orderId, long itemCount, boolean cardAccepted) {
            Span request = tracer.spanBuilder("POST /checkout")
                    .setSpanKind(SpanKind.SERVER)
                    .setAttribute(SemanticAttributes.HTTP_METHOD, "POST")
                    .setAttribute(ORDER_ID, orderId)
                    .setAttribute(ITEM_COUNT, itemCount)
                    .startSpan();
            try (Scope ignored = request.makeCurrent()) {
                boolean charged = chargeCard(orderId);
                request.setAttribute(SemanticAttributes.HTTP_STATUS_CODE, charged ? 201L : 402L);
            } finally {
                request.end();
            }
        }

        private boolean chargeCard(String orderId) {
            Span charge = tracer.spanBuilder("charge-card").setSpanKind(SpanKind.CLIENT).startSpan();
            try (Scope ignored = charge.makeCurrent()) {
                if (orderId.endsWith("2")) {
                    charge.recordException(new IllegalStateException("card declined"));
                    charge.setStatus(StatusCode.ERROR, "card declined");
                    return false;
                }
                charge.addEvent("payment.authorized", Attributes.of(ORDER_ID, orderId));
                return true;
            } finally {
                charge.end();
            }
        }
    }

    /// A span exporter decorator that records each export outcome for the service's health page.
    private static final class ExportOutcomes implements SpanExporter {

        private final SpanExporter delegate;

        private final List<Boolean> results = new CopyOnWriteArrayList<>();

        private final List<Integer> exportedSpanCounts = new CopyOnWriteArrayList<>();

        ExportOutcomes(SpanExporter delegate) {
            this.delegate = delegate;
        }

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            exportedSpanCounts.add(spans.size());
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

    /// An in-process OTLP trace collector that records the requests and headers it receives.
    private static final class Collector extends TraceServiceGrpc.TraceServiceImplBase {

        private final List<ExportTraceServiceRequest> requests = new CopyOnWriteArrayList<>();

        private final List<Metadata> headers = new CopyOnWriteArrayList<>();

        private final AtomicReference<Status> pendingRejection = new AtomicReference<>();

        void rejectNext(Status status) {
            pendingRejection.set(status);
        }

        ServerInterceptor headerRecorder() {
            return new ServerInterceptor() {
                @Override
                public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                        ServerCall<ReqT, RespT> call, Metadata metadata, ServerCallHandler<ReqT, RespT> next) {
                    headers.add(metadata);
                    return next.startCall(call, metadata);
                }
            };
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
