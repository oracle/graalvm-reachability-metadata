/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_grpc_1_6;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.BindableService;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcRequest;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class Opentelemetry_grpc_1_6Test {
    private static final String SERVICE_NAME = "example.EchoService";
    private static final String METHOD_NAME = "Echo";
    private static final String FULL_METHOD_NAME = SERVICE_NAME + "/" + METHOD_NAME;
    private static final Metadata.Key<String> REQUEST_ID_HEADER =
            Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> TRACEPARENT_HEADER =
            Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER);
    private static final MethodDescriptor<String, String> ECHO_METHOD = MethodDescriptor.<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(FULL_METHOD_NAME)
            .setRequestMarshaller(StringMarshaller.INSTANCE)
            .setResponseMarshaller(StringMarshaller.INSTANCE)
            .build();
    private static final AttributeKey<String> EXTRACTOR_PHASE = AttributeKey.stringKey("test.extractor.phase");
    private static final AttributeKey<String> EXTRACTOR_METHOD = AttributeKey.stringKey("test.grpc.method");
    private static final AttributeKey<String> EXTRACTOR_STATUS = AttributeKey.stringKey("test.grpc.status");
    private static final AttributeKey<String> RPC_SYSTEM = AttributeKey.stringKey("rpc.system");
    private static final AttributeKey<String> RPC_SERVICE = AttributeKey.stringKey("rpc.service");
    private static final AttributeKey<String> RPC_METHOD = AttributeKey.stringKey("rpc.method");
    private static final AttributeKey<Long> RPC_GRPC_STATUS_CODE = AttributeKey.longKey("rpc.grpc.status_code");
    private static final AttributeKey<Boolean> GRPC_CANCELED = AttributeKey.booleanKey("grpc.canceled");
    private static final AttributeKey<String> PEER_SERVICE = AttributeKey.stringKey("peer.service");
    private static final AttributeKey<List<String>> CAPTURED_REQUEST_ID =
            AttributeKey.stringArrayKey("rpc.grpc.request.metadata.x-request-id");

    @Test
    void clientAndServerInterceptorsCreateSpansPropagateContextAndCaptureMetadata() throws Exception {
        RecordingSpanExporter exporter = new RecordingSpanExporter();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        GrpcTelemetry telemetry = GrpcTelemetry.builder(openTelemetry)
                .setPeerService("echo-backend")
                .setCapturedClientRequestMetadata(List.of("X-Request-Id"))
                .setCapturedServerRequestMetadata(List.of("x-request-id"))
                .addAttributeExtractor(new TestAttributesExtractor("shared"))
                .addClientAttributeExtractor(new TestAttributesExtractor("client-only"))
                .addServerAttributeExtractor(new TestAttributesExtractor("server-only"))
                .setClientSpanNameExtractor(original -> request -> "client:" + original.extract(request))
                .setServerSpanNameExtractor(original -> request -> "server:" + original.extract(request))
                .build();
        AtomicReference<String> traceparentSeenByServer = new AtomicReference<>();
        AtomicReference<Boolean> serverSpanWasCurrent = new AtomicReference<>(false);
        Server server = null;
        ManagedChannel channel = null;
        try {
            String serverName = InProcessServerBuilder.generateName();
            server = InProcessServerBuilder.forName(serverName)
                    .directExecutor()
                    .addService(new UnaryService(request -> "echo:" + request,
                            (request, headers) -> {
                                traceparentSeenByServer.set(headers.get(TRACEPARENT_HEADER));
                                serverSpanWasCurrent.set(Span.current().getSpanContext().isValid());
                            }))
                    .intercept(telemetry.newServerInterceptor())
                    .intercept(new MetadataCaptureServerInterceptor())
                    .build()
                    .start();
            channel = InProcessChannelBuilder.forName(serverName)
                    .directExecutor()
                    .intercept(telemetry.newClientInterceptor())
                    .build();
            Metadata headers = new Metadata();
            headers.put(REQUEST_ID_HEADER, "request-123");

            String response = unaryCall(channel, ECHO_METHOD, "hello", headers);

            assertThat(response).isEqualTo("echo:hello");
            assertThat(traceparentSeenByServer.get()).isNotBlank();
            assertThat(serverSpanWasCurrent.get()).isTrue();
            List<SpanData> spans = exporter.getFinishedSpanItems();
            assertThat(spans).hasSize(2);
            SpanData clientSpan = singleSpanWithKind(spans, SpanKind.CLIENT);
            SpanData serverSpan = singleSpanWithKind(spans, SpanKind.SERVER);
            assertThat(clientSpan.getName()).isEqualTo("client:" + FULL_METHOD_NAME);
            assertThat(serverSpan.getName()).isEqualTo("server:" + FULL_METHOD_NAME);
            assertCommonGrpcAttributes(clientSpan, Status.OK);
            assertCommonGrpcAttributes(serverSpan, Status.OK);
            assertThat(clientSpan.getAttributes().get(PEER_SERVICE)).isEqualTo("echo-backend");
            assertThat(clientSpan.getAttributes().get(CAPTURED_REQUEST_ID)).containsExactly("request-123");
            assertThat(serverSpan.getAttributes().get(CAPTURED_REQUEST_ID)).containsExactly("request-123");
            assertThat(clientSpan.getAttributes().get(EXTRACTOR_PHASE)).isEqualTo("client-only");
            assertThat(serverSpan.getAttributes().get(EXTRACTOR_PHASE)).isEqualTo("server-only");
            assertThat(clientSpan.getAttributes().get(EXTRACTOR_METHOD)).isEqualTo(FULL_METHOD_NAME);
            assertThat(serverSpan.getAttributes().get(EXTRACTOR_METHOD)).isEqualTo(FULL_METHOD_NAME);
            assertThat(clientSpan.getEvents()).hasSize(2);
            assertThat(serverSpan.getEvents()).hasSize(2);
            assertThat(clientSpan.getParentSpanContext().isValid()).isFalse();
            assertThat(serverSpan.getParentSpanContext().getTraceId())
                    .isEqualTo(clientSpan.getSpanContext().getTraceId());
            assertThat(serverSpan.getParentSpanContext().getSpanId())
                    .isEqualTo(clientSpan.getSpanContext().getSpanId());
        } finally {
            shutdown(channel, server, tracerProvider);
        }
    }

    @Test
    void clientListenerCallbacksRunWithClientSpanContextAndRestoreParentOnClose() throws Exception {
        RecordingSpanExporter exporter = new RecordingSpanExporter();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        GrpcTelemetry telemetry = GrpcTelemetry.create(openTelemetry);
        Server server = null;
        ManagedChannel channel = null;
        try {
            String serverName = InProcessServerBuilder.generateName();
            server = InProcessServerBuilder.forName(serverName)
                    .directExecutor()
                    .addService(new UnaryService(request -> "callback:" + request, (request, headers) -> { }))
                    .build()
                    .start();
            channel = InProcessChannelBuilder.forName(serverName)
                    .directExecutor()
                    .intercept(telemetry.newClientInterceptor())
                    .build();
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<String> response = new AtomicReference<>();
            AtomicReference<Status> status = new AtomicReference<>();
            AtomicReference<Boolean> onMessageSpanWasCurrent = new AtomicReference<>(false);
            AtomicReference<String> onMessageTraceId = new AtomicReference<>();
            AtomicReference<Boolean> onCloseSpanWasCurrent = new AtomicReference<>(true);
            ClientCall<String, String> call = channel.newCall(
                    ECHO_METHOD, CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS));
            call.start(new ClientCall.Listener<>() {
                @Override
                public void onMessage(String message) {
                    response.set(message);
                    onMessageSpanWasCurrent.set(Span.current().getSpanContext().isValid());
                    onMessageTraceId.set(Span.current().getSpanContext().getTraceId());
                }

                @Override
                public void onClose(Status closedStatus, Metadata trailers) {
                    status.set(closedStatus);
                    onCloseSpanWasCurrent.set(Span.current().getSpanContext().isValid());
                    done.countDown();
                }
            }, new Metadata());
            call.request(1);
            call.sendMessage("hello");
            call.halfClose();

            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(status.get()).isNotNull();
            assertThat(status.get().isOk()).isTrue();
            assertThat(response.get()).isEqualTo("callback:hello");
            List<SpanData> spans = waitForFinishedSpans(exporter, 1);
            assertThat(spans).hasSize(1);
            SpanData clientSpan = singleSpanWithKind(spans, SpanKind.CLIENT);
            assertThat(clientSpan.getName()).isEqualTo(FULL_METHOD_NAME);
            assertThat(onMessageSpanWasCurrent.get()).isTrue();
            assertThat(onMessageTraceId.get()).isEqualTo(clientSpan.getSpanContext().getTraceId());
            assertThat(onCloseSpanWasCurrent.get()).isFalse();
            assertThat(clientSpan.getEvents()).hasSize(2);
        } finally {
            shutdown(channel, server, tracerProvider);
        }
    }

    @Test
    void clientReceivesGrpcErrorAndSpansRecordDifferentClientAndServerStatusSemantics() throws Exception {
        RecordingSpanExporter exporter = new RecordingSpanExporter();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        GrpcTelemetry telemetry = GrpcTelemetry.create(openTelemetry);
        Server server = null;
        ManagedChannel channel = null;
        try {
            String serverName = InProcessServerBuilder.generateName();
            server = InProcessServerBuilder.forName(serverName)
                    .directExecutor()
                    .addService(new ErrorService(Status.INVALID_ARGUMENT.withDescription("invalid echo")))
                    .intercept(telemetry.newServerInterceptor())
                    .build()
                    .start();
            channel = InProcessChannelBuilder.forName(serverName)
                    .directExecutor()
                    .intercept(telemetry.newClientInterceptor())
                    .build();

            ManagedChannel finalChannel = channel;
            assertThatThrownBy(() -> unaryCall(finalChannel, ECHO_METHOD, "bad", new Metadata()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(throwable -> assertThat(Status.fromThrowable(throwable).getCode())
                            .isEqualTo(Status.Code.INVALID_ARGUMENT));

            List<SpanData> spans = exporter.getFinishedSpanItems();
            assertThat(spans).hasSize(2);
            SpanData clientSpan = singleSpanWithKind(spans, SpanKind.CLIENT);
            SpanData serverSpan = singleSpanWithKind(spans, SpanKind.SERVER);
            assertThat(clientSpan.getName()).isEqualTo(FULL_METHOD_NAME);
            assertThat(serverSpan.getName()).isEqualTo(FULL_METHOD_NAME);
            assertThat(clientSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            assertThat(serverSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
            assertThat(clientSpan.getAttributes().get(RPC_GRPC_STATUS_CODE))
                    .isEqualTo((long) Status.Code.INVALID_ARGUMENT.value());
            assertThat(serverSpan.getAttributes().get(RPC_GRPC_STATUS_CODE))
                    .isEqualTo((long) Status.Code.INVALID_ARGUMENT.value());
            assertThat(clientSpan.getEvents()).hasSize(1);
            assertThat(serverSpan.getEvents()).hasSize(1);
        } finally {
            shutdown(channel, server, tracerProvider);
        }
    }

    @Test
    void serverCancellationAttributeIsRecordedWhenExperimentalSpanAttributesAreEnabled() throws Exception {
        RecordingSpanExporter exporter = new RecordingSpanExporter();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        GrpcTelemetry telemetry = GrpcTelemetry.builder(openTelemetry)
                .setCaptureExperimentalSpanAttributes(true)
                .build();
        CountDownLatch serviceStarted = new CountDownLatch(1);
        CountDownLatch serviceCancelled = new CountDownLatch(1);
        Server server = null;
        ManagedChannel channel = null;
        try {
            String serverName = InProcessServerBuilder.generateName();
            server = InProcessServerBuilder.forName(serverName)
                    .directExecutor()
                    .addService(new CancellableUnaryService(serviceStarted, serviceCancelled))
                    .intercept(telemetry.newServerInterceptor())
                    .build()
                    .start();
            channel = InProcessChannelBuilder.forName(serverName)
                    .directExecutor()
                    .intercept(telemetry.newClientInterceptor())
                    .build();
            CountDownLatch clientClosed = new CountDownLatch(1);
            AtomicReference<Status> clientStatus = new AtomicReference<>();
            ClientCall<String, String> call = channel.newCall(
                    ECHO_METHOD, CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS));
            call.start(new ClientCall.Listener<>() {
                @Override
                public void onClose(Status status, Metadata trailers) {
                    clientStatus.set(status);
                    clientClosed.countDown();
                }
            }, new Metadata());
            call.request(1);
            call.sendMessage("cancel-me");
            call.halfClose();

            assertThat(serviceStarted.await(5, TimeUnit.SECONDS)).isTrue();
            call.cancel("client cancelled", null);
            assertThat(clientClosed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(serviceCancelled.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(clientStatus.get().getCode()).isEqualTo(Status.Code.CANCELLED);
            List<SpanData> spans = waitForFinishedSpans(exporter, 2);
            assertThat(spans).hasSize(2);
            SpanData clientSpan = singleSpanWithKind(spans, SpanKind.CLIENT);
            SpanData serverSpan = singleSpanWithKind(spans, SpanKind.SERVER);
            assertThat(clientSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            assertThat(serverSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
            assertThat(clientSpan.getAttributes().get(RPC_GRPC_STATUS_CODE))
                    .isEqualTo((long) Status.Code.CANCELLED.value());
            assertThat(serverSpan.getAttributes().get(RPC_GRPC_STATUS_CODE))
                    .isEqualTo((long) Status.Code.CANCELLED.value());
            assertThat(serverSpan.getAttributes().get(GRPC_CANCELED)).isTrue();
        } finally {
            shutdown(channel, server, tracerProvider);
        }
    }

    private static <T> T unaryCall(
            ManagedChannel channel, MethodDescriptor<T, T> method, T request, Metadata headers) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> response = new AtomicReference<>();
        AtomicReference<Status> status = new AtomicReference<>();
        AtomicReference<Metadata> trailers = new AtomicReference<>();
        ClientCall<T, T> call = channel.newCall(method, CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS));
        call.start(new ClientCall.Listener<>() {
            @Override
            public void onMessage(T message) {
                response.set(message);
            }

            @Override
            public void onClose(Status closedStatus, Metadata closedTrailers) {
                status.set(closedStatus);
                trailers.set(closedTrailers);
                done.countDown();
            }
        }, headers);
        call.request(1);
        call.sendMessage(request);
        call.halfClose();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        Status closedStatus = status.get();
        if (closedStatus == null || !closedStatus.isOk()) {
            throw closedStatus == null
                    ? Status.UNKNOWN.withDescription("call did not close").asRuntimeException(trailers.get())
                    : closedStatus.asRuntimeException(trailers.get());
        }
        return response.get();
    }

    private static SpanData singleSpanWithKind(List<SpanData> spans, SpanKind spanKind) {
        List<SpanData> matchingSpans = spans.stream()
                .filter(span -> span.getKind() == spanKind)
                .toList();
        assertThat(matchingSpans).hasSize(1);
        return matchingSpans.get(0);
    }

    private static List<SpanData> waitForFinishedSpans(
            RecordingSpanExporter exporter, int expectedSpanCount) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        List<SpanData> spans = exporter.getFinishedSpanItems();
        while (spans.size() < expectedSpanCount && System.nanoTime() < deadline) {
            Thread.sleep(10);
            spans = exporter.getFinishedSpanItems();
        }
        return spans;
    }

    private static void assertCommonGrpcAttributes(SpanData span, Status status) {
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
        assertThat(span.getAttributes().get(RPC_SYSTEM)).isEqualTo("grpc");
        assertThat(span.getAttributes().get(RPC_SERVICE)).isEqualTo(SERVICE_NAME);
        assertThat(span.getAttributes().get(RPC_METHOD)).isEqualTo(METHOD_NAME);
        assertThat(span.getAttributes().get(RPC_GRPC_STATUS_CODE)).isEqualTo((long) status.getCode().value());
        assertThat(span.getAttributes().get(EXTRACTOR_STATUS)).isEqualTo(status.getCode().name());
    }

    private static void shutdown(
            ManagedChannel channel,
            Server server,
            SdkTracerProvider tracerProvider) throws Exception {
        if (channel != null) {
            channel.shutdownNow();
            assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        if (server != null) {
            server.shutdownNow();
            assertThat(server.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        tracerProvider.close();
    }

    private enum StringMarshaller implements MethodDescriptor.Marshaller<String> {
        INSTANCE;

        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to parse gRPC test payload", e);
            }
        }
    }

    private interface UnaryResponder {
        String respond(String request);
    }

    private interface UnaryRequestObserver {
        void observe(String request, Metadata headers);
    }

    private static final class UnaryService implements BindableService {
        private final UnaryResponder responder;
        private final UnaryRequestObserver observer;

        private UnaryService(UnaryResponder responder, UnaryRequestObserver observer) {
            this.responder = responder;
            this.observer = observer;
        }

        @Override
        public ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE_NAME)
                    .addMethod(ECHO_METHOD, ServerCalls.asyncUnaryCall((request, responseObserver) -> {
                        try {
                            observer.observe(request, MetadataUtilsHolder.REQUEST_HEADERS.get());
                            responseObserver.onNext(responder.respond(request));
                            responseObserver.onCompleted();
                        } finally {
                            MetadataUtilsHolder.REQUEST_HEADERS.remove();
                        }
                    }))
                    .build();
        }
    }

    private static final class ErrorService implements BindableService {
        private final Status status;

        private ErrorService(Status status) {
            this.status = status;
        }

        @Override
        public ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE_NAME)
                    .addMethod(ECHO_METHOD, ServerCalls.asyncUnaryCall((request, responseObserver) ->
                            responseObserver.onError(status.asRuntimeException())))
                    .build();
        }
    }

    private static final class CancellableUnaryService implements BindableService {
        private final CountDownLatch started;
        private final CountDownLatch cancelled;

        private CancellableUnaryService(CountDownLatch started, CountDownLatch cancelled) {
            this.started = started;
            this.cancelled = cancelled;
        }

        @Override
        public ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE_NAME)
                    .addMethod(ECHO_METHOD, ServerCalls.asyncUnaryCall(this::waitForCancellation))
                    .build();
        }

        @SuppressWarnings("unchecked")
        private void waitForCancellation(String request, StreamObserver<String> responseObserver) {
            ServerCallStreamObserver<String> serverObserver =
                    (ServerCallStreamObserver<String>) responseObserver;
            serverObserver.setOnCancelHandler(cancelled::countDown);
            started.countDown();
        }
    }

    private static final class MetadataCaptureServerInterceptor implements ServerInterceptor {
        @Override
        public <REQUEST, RESPONSE> ServerCall.Listener<REQUEST> interceptCall(
                ServerCall<REQUEST, RESPONSE> call,
                Metadata headers,
                ServerCallHandler<REQUEST, RESPONSE> next) {
            MetadataUtilsHolder.REQUEST_HEADERS.set(headers);
            return next.startCall(call, headers);
        }
    }

    private static final class MetadataUtilsHolder {
        private static final ThreadLocal<Metadata> REQUEST_HEADERS = ThreadLocal.withInitial(Metadata::new);
    }

    private static final class TestAttributesExtractor implements AttributesExtractor<GrpcRequest, Status> {
        private final String phase;

        private TestAttributesExtractor(String phase) {
            this.phase = phase;
        }

        @Override
        public void onStart(AttributesBuilder attributes, Context parentContext, GrpcRequest request) {
            attributes.put(EXTRACTOR_METHOD, request.getMethod().getFullMethodName());
        }

        @Override
        public void onEnd(
                AttributesBuilder attributes,
                Context context,
                GrpcRequest request,
                Status status,
                Throwable error) {
            attributes.put(EXTRACTOR_PHASE, phase);
            if (status != null) {
                attributes.put(EXTRACTOR_STATUS, status.getCode().name());
            }
        }
    }

    private static final class RecordingSpanExporter implements SpanExporter {
        private final List<SpanData> finishedSpans = new ArrayList<>();

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            finishedSpans.addAll(spans);
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

        private List<SpanData> getFinishedSpanItems() {
            return List.copyOf(finishedSpans);
        }
    }
}
