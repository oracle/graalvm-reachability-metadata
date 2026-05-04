/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_opentelemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.opentelemetry.GrpcOpenTelemetry;
import io.grpc.opentelemetry.GrpcTraceBinContextPropagator;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class Grpc_opentelemetryTest {
    private static final String TRACE_ID = "e384981d65129fa3e384981d65129fa3";
    private static final String SPAN_ID = "e384981d65129fa3";
    private static final TextMapSetter<Map<String, String>> MAP_SETTER = (carrier, key, value) -> {
        if (carrier != null) {
            carrier.put(key, value);
        }
    };
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
    private static final MethodDescriptor<String, String> ECHO_METHOD = MethodDescriptor.<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(MethodDescriptor.generateFullMethodName("test.EchoService", "Echo"))
            .setRequestMarshaller(new Utf8Marshaller())
            .setResponseMarshaller(new Utf8Marshaller())
            .build();

    @Test
    void grpcTraceBinPropagatorAdvertisesSingletonField() {
        GrpcTraceBinContextPropagator propagator = GrpcTraceBinContextPropagator.defaultInstance();

        assertThat(GrpcTraceBinContextPropagator.defaultInstance()).isSameAs(propagator);
        assertThat(propagator.fields()).containsExactly(GrpcTraceBinContextPropagator.GRPC_TRACE_BIN_HEADER);
    }

    @Test
    void grpcTraceBinPropagatorInjectsAndExtractsSampledTextCarrier() {
        GrpcTraceBinContextPropagator propagator = GrpcTraceBinContextPropagator.defaultInstance();
        SpanContext originalSpanContext = sampledSpanContext();
        Context originalContext = Context.root().with(Span.wrap(originalSpanContext));
        Map<String, String> carrier = new HashMap<>();

        propagator.inject(originalContext, carrier, MAP_SETTER);

        assertThat(carrier).containsOnlyKeys(GrpcTraceBinContextPropagator.GRPC_TRACE_BIN_HEADER);
        assertThat(carrier.get(GrpcTraceBinContextPropagator.GRPC_TRACE_BIN_HEADER))
                .isNotBlank()
                .doesNotContain("=");

        Context extractedContext = propagator.extract(Context.root(), carrier, MAP_GETTER);
        SpanContext extractedSpanContext = Span.fromContext(extractedContext).getSpanContext();

        assertThat(extractedSpanContext).isEqualTo(originalSpanContext);
        assertThat(extractedSpanContext.getTraceFlags()).isEqualTo(TraceFlags.getSampled());
    }

    @Test
    void grpcTraceBinPropagatorPreservesUnsampledTraceFlags() {
        GrpcTraceBinContextPropagator propagator = GrpcTraceBinContextPropagator.defaultInstance();
        SpanContext originalSpanContext = SpanContext.create(
                TRACE_ID,
                SPAN_ID,
                TraceFlags.getDefault(),
                TraceState.getDefault());
        Map<String, String> carrier = new HashMap<>();

        propagator.inject(Context.root().with(Span.wrap(originalSpanContext)), carrier, MAP_SETTER);
        Context extractedContext = propagator.extract(Context.root(), carrier, MAP_GETTER);

        SpanContext extractedSpanContext = Span.fromContext(extractedContext).getSpanContext();

        assertThat(extractedSpanContext).isEqualTo(originalSpanContext);
        assertThat(extractedSpanContext.getTraceFlags()).isEqualTo(TraceFlags.getDefault());
    }

    @Test
    void grpcTraceBinPropagatorIgnoresNullInvalidAndMalformedInputs() {
        GrpcTraceBinContextPropagator propagator = GrpcTraceBinContextPropagator.defaultInstance();
        Map<String, String> carrier = new HashMap<>();

        propagator.inject(null, carrier, MAP_SETTER);
        propagator.inject(Context.root().with(Span.wrap(SpanContext.getInvalid())), carrier, MAP_SETTER);

        assertThat(carrier).isEmpty();
        assertThat(propagator.extract(null, carrier, MAP_GETTER)).isSameAs(Context.root());

        Context existingContext = Context.root().with(Span.wrap(sampledSpanContext()));
        Map<String, String> malformedCarrier = Map.of(
                GrpcTraceBinContextPropagator.GRPC_TRACE_BIN_HEADER,
                "not-base64");

        assertThat(propagator.extract(existingContext, malformedCarrier, MAP_GETTER)).isSameAs(existingContext);
        assertThat(propagator.extract(existingContext, carrier, null)).isSameAs(existingContext);
    }

    @Test
    void grpcOpenTelemetryConfiguresInProcessClientAndServerBuilders() throws Exception {
        AtomicInteger targetFilterCalls = new AtomicInteger();
        GrpcOpenTelemetry openTelemetry = GrpcOpenTelemetry.newBuilder()
                .disableAllMetrics()
                .enableMetrics(List.of("grpc.client.call.duration", "grpc.server.call.duration"))
                .disableMetrics(List.of("grpc.client.call.retries"))
                .addOptionalLabel("grpc.lb.locality")
                .targetAttributeFilter(target -> {
                    targetFilterCalls.incrementAndGet();
                    return target.contains("otel-test");
                })
                .build();
        String serverName = "otel-test-" + UUID.randomUUID();
        InProcessServerBuilder serverBuilder = InProcessServerBuilder.forName(serverName).directExecutor();
        openTelemetry.configureServerBuilder(serverBuilder);
        Server server = serverBuilder.addService(echoService()).build().start();
        InProcessChannelBuilder channelBuilder = InProcessChannelBuilder.forName(serverName).directExecutor();
        openTelemetry.configureChannelBuilder(channelBuilder);
        ManagedChannel channel = channelBuilder.build();
        try {
            String response = ClientCalls.blockingUnaryCall(
                    channel,
                    ECHO_METHOD,
                    CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS),
                    "alpha");

            assertThat(response).isEqualTo("echo:alpha");
            assertThat(targetFilterCalls.get()).isGreaterThanOrEqualTo(1);
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
            assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(server.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void grpcOpenTelemetryRegisterGlobalAppliesToNewBuilders() throws Exception {
        AtomicInteger targetFilterCalls = new AtomicInteger();
        GrpcOpenTelemetry openTelemetry = GrpcOpenTelemetry.newBuilder()
                .targetAttributeFilter(target -> {
                    targetFilterCalls.incrementAndGet();
                    return target.contains("otel-global-test");
                })
                .build();
        openTelemetry.registerGlobal();
        String serverName = "otel-global-test-" + UUID.randomUUID();
        Server server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(echoService())
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        try {
            String response = ClientCalls.blockingUnaryCall(
                    channel,
                    ECHO_METHOD,
                    CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS),
                    "global");

            assertThat(response).isEqualTo("echo:global");
            assertThat(targetFilterCalls.get()).isGreaterThanOrEqualTo(1);
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
            assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(server.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void grpcOpenTelemetryUsesExplicitOpenTelemetrySdkForInstrumentation() throws Exception {
        RecordingOpenTelemetry recordingOpenTelemetry = new RecordingOpenTelemetry();
        GrpcOpenTelemetry openTelemetry = GrpcOpenTelemetry.newBuilder()
                .sdk(recordingOpenTelemetry)
                .build();
        String serverName = "otel-sdk-test-" + UUID.randomUUID();
        InProcessServerBuilder serverBuilder = InProcessServerBuilder.forName(serverName).directExecutor();
        openTelemetry.configureServerBuilder(serverBuilder);
        Server server = serverBuilder.addService(echoService()).build().start();
        InProcessChannelBuilder channelBuilder = InProcessChannelBuilder.forName(serverName).directExecutor();
        openTelemetry.configureChannelBuilder(channelBuilder);
        ManagedChannel channel = channelBuilder.build();
        try {
            String response = ClientCalls.blockingUnaryCall(
                    channel,
                    ECHO_METHOD,
                    CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS),
                    "sdk");

            assertThat(response).isEqualTo("echo:sdk");
            assertThat(recordingOpenTelemetry.meterProviderRequests()).isGreaterThanOrEqualTo(1);
            assertThat(recordingOpenTelemetry.meterBuilderRequests()).isGreaterThanOrEqualTo(1);
            assertThat(recordingOpenTelemetry.tracerProviderRequests()).isGreaterThanOrEqualTo(1);
            assertThat(recordingOpenTelemetry.tracerBuilderRequests()).isGreaterThanOrEqualTo(1);
            assertThat(recordingOpenTelemetry.propagatorRequests()).isGreaterThanOrEqualTo(1);
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
            assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(server.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void grpcOpenTelemetryDoesNotMaskRpcFailures() throws Exception {
        GrpcOpenTelemetry openTelemetry = GrpcOpenTelemetry.newBuilder().build();
        String serverName = "otel-failure-test-" + UUID.randomUUID();
        InProcessServerBuilder serverBuilder = InProcessServerBuilder.forName(serverName).directExecutor();
        openTelemetry.configureServerBuilder(serverBuilder);
        Server server = serverBuilder.addService(failingService()).build().start();
        InProcessChannelBuilder channelBuilder = InProcessChannelBuilder.forName(serverName).directExecutor();
        openTelemetry.configureChannelBuilder(channelBuilder);
        ManagedChannel channel = channelBuilder.build();
        try {
            StatusRuntimeException exception = assertThrows(
                    StatusRuntimeException.class,
                    () -> ClientCalls.blockingUnaryCall(
                            channel,
                            ECHO_METHOD,
                            CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS),
                            "boom"));

            assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
            assertThat(exception.getStatus().getDescription()).isEqualTo("denied:boom");
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
            assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(server.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static SpanContext sampledSpanContext() {
        return SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
    }

    private static ServerServiceDefinition echoService() {
        ServerCallHandler<String, String> handler = ServerCalls.asyncUnaryCall(
                new ServerCalls.UnaryMethod<>() {
                    @Override
                    public void invoke(String request, StreamObserver<String> responseObserver) {
                        responseObserver.onNext("echo:" + request);
                        responseObserver.onCompleted();
                    }
                });
        return ServerServiceDefinition.builder("test.EchoService")
                .addMethod(ECHO_METHOD, handler)
                .build();
    }

    private static ServerServiceDefinition failingService() {
        ServerCallHandler<String, String> handler = ServerCalls.asyncUnaryCall(
                new ServerCalls.UnaryMethod<>() {
                    @Override
                    public void invoke(String request, StreamObserver<String> responseObserver) {
                        responseObserver.onError(Status.PERMISSION_DENIED
                                .withDescription("denied:" + request)
                                .asRuntimeException());
                    }
                });
        return ServerServiceDefinition.builder("test.EchoService")
                .addMethod(ECHO_METHOD, handler)
                .build();
    }

    private static final class RecordingOpenTelemetry implements OpenTelemetry {
        private final OpenTelemetry delegate = OpenTelemetry.noop();
        private final RecordingMeterProvider meterProvider = new RecordingMeterProvider();
        private final RecordingTracerProvider tracerProvider = new RecordingTracerProvider();
        private final AtomicInteger meterProviderRequests = new AtomicInteger();
        private final AtomicInteger tracerProviderRequests = new AtomicInteger();
        private final AtomicInteger propagatorRequests = new AtomicInteger();

        @Override
        public TracerProvider getTracerProvider() {
            tracerProviderRequests.incrementAndGet();
            return tracerProvider;
        }

        @Override
        public MeterProvider getMeterProvider() {
            meterProviderRequests.incrementAndGet();
            return meterProvider;
        }

        @Override
        public ContextPropagators getPropagators() {
            propagatorRequests.incrementAndGet();
            return delegate.getPropagators();
        }

        private int meterProviderRequests() {
            return meterProviderRequests.get();
        }

        private int meterBuilderRequests() {
            return meterProvider.meterBuilderRequests();
        }

        private int tracerProviderRequests() {
            return tracerProviderRequests.get();
        }

        private int tracerBuilderRequests() {
            return tracerProvider.tracerBuilderRequests();
        }

        private int propagatorRequests() {
            return propagatorRequests.get();
        }
    }

    private static final class RecordingMeterProvider implements MeterProvider {
        private final MeterProvider delegate = MeterProvider.noop();
        private final AtomicInteger meterBuilderRequests = new AtomicInteger();

        @Override
        public MeterBuilder meterBuilder(String instrumentationScopeName) {
            meterBuilderRequests.incrementAndGet();
            return delegate.meterBuilder(instrumentationScopeName);
        }

        private int meterBuilderRequests() {
            return meterBuilderRequests.get();
        }
    }

    private static final class RecordingTracerProvider implements TracerProvider {
        private final TracerProvider delegate = TracerProvider.noop();
        private final AtomicInteger tracerBuilderRequests = new AtomicInteger();

        @Override
        public Tracer get(String instrumentationScopeName) {
            return delegate.get(instrumentationScopeName);
        }

        @Override
        public Tracer get(String instrumentationScopeName, String instrumentationScopeVersion) {
            return delegate.get(instrumentationScopeName, instrumentationScopeVersion);
        }

        @Override
        public TracerBuilder tracerBuilder(String instrumentationScopeName) {
            tracerBuilderRequests.incrementAndGet();
            return delegate.tracerBuilder(instrumentationScopeName);
        }

        private int tracerBuilderRequests() {
            return tracerBuilderRequests.get();
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
                throw new IllegalArgumentException("Unable to parse UTF-8 gRPC payload", exception);
            }
        }
    }
}
