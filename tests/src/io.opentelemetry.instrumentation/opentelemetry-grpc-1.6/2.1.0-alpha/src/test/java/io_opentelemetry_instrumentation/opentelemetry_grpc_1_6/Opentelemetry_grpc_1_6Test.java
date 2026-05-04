/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_grpc_1_6;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.grpc.CallOptions;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcRequest;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class Opentelemetry_grpc_1_6Test {
    private static final AttributeKey<String> RPC_SYSTEM = AttributeKey.stringKey("rpc.system");
    private static final AttributeKey<String> RPC_SERVICE = AttributeKey.stringKey("rpc.service");
    private static final AttributeKey<String> RPC_METHOD = AttributeKey.stringKey("rpc.method");
    private static final AttributeKey<String> PEER_SERVICE = AttributeKey.stringKey("peer.service");
    private static final AttributeKey<List<String>> CAPTURED_METADATA = AttributeKey.stringArrayKey(
            "rpc.grpc.request.metadata.x-test-header");
    private static final AttributeKey<String> COMMON_ATTRIBUTE = AttributeKey.stringKey("test.common.method");
    private static final AttributeKey<String> CLIENT_ATTRIBUTE = AttributeKey.stringKey("test.client.status");
    private static final AttributeKey<String> SERVER_ATTRIBUTE = AttributeKey.stringKey("test.server.status");
    private static final Metadata.Key<String> TEST_HEADER = Metadata.Key.of(
            "x-test-header",
            Metadata.ASCII_STRING_MARSHALLER);
    private static final String SERVICE_NAME = "test.EchoService";
    private static final String METHOD_NAME = "Echo";
    private static final String FULL_METHOD_NAME = MethodDescriptor.generateFullMethodName(SERVICE_NAME, METHOD_NAME);
    private static final MethodDescriptor<String, String> ECHO_METHOD = MethodDescriptor.<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(FULL_METHOD_NAME)
            .setRequestMarshaller(new Utf8Marshaller())
            .setResponseMarshaller(new Utf8Marshaller())
            .build();

    @Test
    void createInstrumentsSuccessfulUnaryCallsAndPropagatesParentTrace() throws Exception {
        try (TestingOpenTelemetry testing = TestingOpenTelemetry.create()) {
            GrpcTelemetry telemetry = GrpcTelemetry.create(testing.openTelemetry());
            String serverName = "otel-grpc-success-" + UUID.randomUUID();
            Server server = startServer(serverName, telemetry, echoService());
            ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
            Span parentSpan = testing.openTelemetry()
                    .getTracer("grpc-test")
                    .spanBuilder("parent-operation")
                    .startSpan();
            try (Scope ignored = parentSpan.makeCurrent()) {
                String response = ClientCalls.blockingUnaryCall(
                        ClientInterceptors.intercept(channel, telemetry.newClientInterceptor()),
                        ECHO_METHOD,
                        CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS),
                        "alpha");

                assertThat(response).isEqualTo("echo:alpha");
            } finally {
                parentSpan.end();
                shutdown(channel, server);
            }

            List<SpanData> spans = testing.finishedSpans();
            SpanData clientSpan = findSpan(spans, FULL_METHOD_NAME, SpanKind.CLIENT);
            SpanData serverSpan = findSpan(spans, FULL_METHOD_NAME, SpanKind.SERVER);
            SpanData parent = findSpan(spans, "parent-operation", SpanKind.INTERNAL);

            assertThat(clientSpan.getTraceId()).isEqualTo(parent.getTraceId());
            assertThat(clientSpan.getParentSpanId()).isEqualTo(parent.getSpanId());
            assertThat(serverSpan.getTraceId()).isEqualTo(clientSpan.getTraceId());
            assertThat(serverSpan.getParentSpanId()).isEqualTo(clientSpan.getSpanId());
            assertThat(clientSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
            assertThat(serverSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
            assertStandardGrpcAttributes(clientSpan);
            assertStandardGrpcAttributes(serverSpan);
        }
    }

    @Test
    void builderOptionsCustomizeNamesAttributesPeerServiceAndCapturedMetadata() throws Exception {
        try (TestingOpenTelemetry testing = TestingOpenTelemetry.create()) {
            GrpcTelemetry telemetry = GrpcTelemetry.builder(testing.openTelemetry())
                    .setClientSpanNameExtractor(
                            defaultExtractor -> request -> "client:" + defaultExtractor.extract(request))
                    .setServerSpanNameExtractor(
                            defaultExtractor -> request -> "server:" + defaultExtractor.extract(request))
                    .setPeerService("orders-backend")
                    .setCapturedClientRequestMetadata(List.of(TEST_HEADER.name()))
                    .setCapturedServerRequestMetadata(List.of(TEST_HEADER.name().toUpperCase()))
                    .addAttributeExtractor(new MethodAttributeExtractor())
                    .addClientAttributeExtractor(new StatusAttributeExtractor(CLIENT_ATTRIBUTE))
                    .addServerAttributeExtractor(new StatusAttributeExtractor(SERVER_ATTRIBUTE))
                    .build();
            String serverName = "otel-grpc-builder-" + UUID.randomUUID();
            Server server = startServer(serverName, telemetry, echoService());
            ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
            Metadata headers = new Metadata();
            headers.put(TEST_HEADER, "metadata-value");
            try {
                String response = ClientCalls.blockingUnaryCall(
                        ClientInterceptors.intercept(
                                channel,
                                MetadataUtils.newAttachHeadersInterceptor(headers),
                                telemetry.newClientInterceptor()),
                        ECHO_METHOD,
                        CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS),
                        "custom");

                assertThat(response).isEqualTo("echo:custom");
            } finally {
                shutdown(channel, server);
            }

            SpanData clientSpan = findSpan(testing.finishedSpans(), "client:" + FULL_METHOD_NAME, SpanKind.CLIENT);
            SpanData serverSpan = findSpan(testing.finishedSpans(), "server:" + FULL_METHOD_NAME, SpanKind.SERVER);

            assertThat(clientSpan.getAttributes().get(PEER_SERVICE)).isEqualTo("orders-backend");
            assertThat(clientSpan.getAttributes().get(COMMON_ATTRIBUTE)).isEqualTo(FULL_METHOD_NAME);
            assertThat(serverSpan.getAttributes().get(COMMON_ATTRIBUTE)).isEqualTo(FULL_METHOD_NAME);
            assertThat(clientSpan.getAttributes().get(CLIENT_ATTRIBUTE)).isEqualTo(Status.OK.getCode().name());
            assertThat(serverSpan.getAttributes().get(SERVER_ATTRIBUTE)).isEqualTo(Status.OK.getCode().name());
            assertThat(clientSpan.getAttributes().get(CAPTURED_METADATA)).containsExactly("metadata-value");
            assertThat(serverSpan.getAttributes().get(CAPTURED_METADATA)).containsExactly("metadata-value");
        }
    }

    @Test
    void failedUnaryCallsPreserveGrpcStatusAndRecordErroredSpans() throws Exception {
        try (TestingOpenTelemetry testing = TestingOpenTelemetry.create()) {
            GrpcTelemetry telemetry = GrpcTelemetry.create(testing.openTelemetry());
            String serverName = "otel-grpc-failure-" + UUID.randomUUID();
            Server server = startServer(serverName, telemetry, failingService());
            ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
            try {
                StatusRuntimeException exception = assertThrows(
                        StatusRuntimeException.class,
                        () -> ClientCalls.blockingUnaryCall(
                                ClientInterceptors.intercept(channel, telemetry.newClientInterceptor()),
                                ECHO_METHOD,
                                CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS),
                                "denied"));

                assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
                assertThat(exception.getStatus().getDescription()).isEqualTo("denied:denied");
            } finally {
                shutdown(channel, server);
            }

            SpanData clientSpan = findSpan(testing.finishedSpans(), FULL_METHOD_NAME, SpanKind.CLIENT);
            SpanData serverSpan = findSpan(testing.finishedSpans(), FULL_METHOD_NAME, SpanKind.SERVER);

            assertThat(clientSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            assertThat(serverSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            assertStandardGrpcAttributes(clientSpan);
            assertStandardGrpcAttributes(serverSpan);
        }
    }

    @Test
    void noopTelemetryProvidesReusableClientAndServerInterceptors() throws Exception {
        GrpcTelemetry telemetry = GrpcTelemetry.create(OpenTelemetry.noop());
        String serverName = "otel-grpc-noop-" + UUID.randomUUID();
        Server server = startServer(serverName, telemetry, echoService());
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        try {
            String response = ClientCalls.blockingUnaryCall(
                    ClientInterceptors.intercept(channel, telemetry.newClientInterceptor()),
                    ECHO_METHOD,
                    CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS),
                    "noop");

            assertThat(response).isEqualTo("echo:noop");
            assertThat(telemetry.newClientInterceptor()).isNotNull();
            assertThat(telemetry.newServerInterceptor()).isNotNull();
        } finally {
            shutdown(channel, server);
        }
    }

    private static Server startServer(
            String serverName,
            GrpcTelemetry telemetry,
            ServerServiceDefinition serviceDefinition) throws IOException {
        return InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(ServerInterceptors.intercept(serviceDefinition, telemetry.newServerInterceptor()))
                .build()
                .start();
    }

    private static ServerServiceDefinition echoService() {
        ServerCallHandler<String, String> handler = ServerCalls.asyncUnaryCall(new ServerCalls.UnaryMethod<>() {
            @Override
            public void invoke(String request, StreamObserver<String> responseObserver) {
                responseObserver.onNext("echo:" + request);
                responseObserver.onCompleted();
            }
        });
        return ServerServiceDefinition.builder(SERVICE_NAME).addMethod(ECHO_METHOD, handler).build();
    }

    private static ServerServiceDefinition failingService() {
        ServerCallHandler<String, String> handler = ServerCalls.asyncUnaryCall(new ServerCalls.UnaryMethod<>() {
            @Override
            public void invoke(String request, StreamObserver<String> responseObserver) {
                responseObserver.onError(Status.INTERNAL
                        .withDescription("denied:" + request)
                        .asRuntimeException());
            }
        });
        return ServerServiceDefinition.builder(SERVICE_NAME).addMethod(ECHO_METHOD, handler).build();
    }

    private static void shutdown(ManagedChannel channel, Server server) throws InterruptedException {
        channel.shutdownNow();
        server.shutdownNow();
        assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(server.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    private static SpanData findSpan(List<SpanData> spans, String name, SpanKind kind) {
        return spans.stream()
                .filter(span -> span.getName().equals(name))
                .filter(span -> span.getKind().equals(kind))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + kind + " span named " + name + " in " + spans));
    }

    private static void assertStandardGrpcAttributes(SpanData span) {
        assertThat(span.getAttributes().get(RPC_SYSTEM)).isEqualTo("grpc");
        assertThat(span.getAttributes().get(RPC_SERVICE)).isEqualTo(SERVICE_NAME);
        assertThat(span.getAttributes().get(RPC_METHOD)).isEqualTo(METHOD_NAME);
    }

    private static final class MethodAttributeExtractor implements AttributesExtractor<GrpcRequest, Status> {
        @Override
        public void onStart(AttributesBuilder attributes, Context parentContext, GrpcRequest request) {
            attributes.put(COMMON_ATTRIBUTE, request.getMethod().getFullMethodName());
        }

        @Override
        public void onEnd(
                AttributesBuilder attributes,
                Context context,
                GrpcRequest request,
                Status response,
                Throwable error) {
            // This extractor only records request data available at span start.
        }
    }

    private static final class StatusAttributeExtractor implements AttributesExtractor<GrpcRequest, Status> {
        private final AttributeKey<String> attributeKey;

        StatusAttributeExtractor(AttributeKey<String> attributeKey) {
            this.attributeKey = attributeKey;
        }

        @Override
        public void onStart(AttributesBuilder attributes, Context parentContext, GrpcRequest request) {
            // This extractor only records the final gRPC status.
        }

        @Override
        public void onEnd(
                AttributesBuilder attributes,
                Context context,
                GrpcRequest request,
                Status response,
                Throwable error) {
            if (response != null) {
                attributes.put(attributeKey, response.getCode().name());
            }
        }
    }

    private static final class TestingOpenTelemetry implements AutoCloseable {
        private final InMemorySpanExporter spanExporter;
        private final SdkTracerProvider tracerProvider;
        private final OpenTelemetrySdk openTelemetry;

        private TestingOpenTelemetry(
                InMemorySpanExporter spanExporter,
                SdkTracerProvider tracerProvider,
                OpenTelemetrySdk openTelemetry) {
            this.spanExporter = spanExporter;
            this.tracerProvider = tracerProvider;
            this.openTelemetry = openTelemetry;
        }

        static TestingOpenTelemetry create() {
            InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
            SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                    .build();
            OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                    .build();
            return new TestingOpenTelemetry(spanExporter, tracerProvider, openTelemetry);
        }

        OpenTelemetrySdk openTelemetry() {
            return openTelemetry;
        }

        List<SpanData> finishedSpans() {
            return spanExporter.getFinishedSpanItems();
        }

        @Override
        public void close() {
            tracerProvider.close();
            spanExporter.shutdown();
        }
    }

    private static final class Utf8Marshaller implements MethodDescriptor.Marshaller<String> {
        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to parse gRPC test payload", exception);
            }
        }
    }
}
