/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api.gax_grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.api.gax.grpc.ChannelPoolSettings;
import com.google.api.gax.grpc.GaxGrpcProperties;
import com.google.api.gax.grpc.GrpcCallContext;
import com.google.api.gax.grpc.GrpcCallSettings;
import com.google.api.gax.grpc.GrpcHeaderInterceptor;
import com.google.api.gax.grpc.GrpcRawCallableFactory;
import com.google.api.gax.grpc.GrpcResponseMetadata;
import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.grpc.InstantiatingGrpcChannelProvider;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.ApiStreamObserver;
import com.google.api.gax.rpc.ClientStreamingCallable;
import com.google.api.gax.rpc.ServerStreamingCallable;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.protobuf.StringValue;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class Gax_grpcTest {
    private static final String SERVICE_NAME = "reachability.EchoService";
    private static final Metadata.Key<String> TEST_HEADER = Metadata.Key.of(
            "x-test-header", Metadata.ASCII_STRING_MARSHALLER);
    private static final MethodDescriptor<StringValue, StringValue> ECHO_METHOD = MethodDescriptor
            .<StringValue, StringValue>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "Echo"))
            .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
            .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
            .build();
    private static final MethodDescriptor<StringValue, StringValue> EXPAND_METHOD = MethodDescriptor
            .<StringValue, StringValue>newBuilder()
            .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
            .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "Expand"))
            .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
            .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
            .build();
    private static final MethodDescriptor<StringValue, StringValue> COLLECT_METHOD = MethodDescriptor
            .<StringValue, StringValue>newBuilder()
            .setType(MethodDescriptor.MethodType.CLIENT_STREAMING)
            .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "Collect"))
            .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
            .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
            .build();

    @Test
    void rawUnaryCallableUsesGrpcContextHeadersAndCallSettings() throws Exception {
        AtomicReference<Metadata> receivedHeaders = new AtomicReference<>();
        ServerServiceDefinition service = ServerInterceptors.intercept(echoService(), captureHeaders(receivedHeaders));
        InProcessFixture fixture = InProcessFixture.start(service);

        try {
            GrpcCallSettings<StringValue, StringValue> callSettings = GrpcCallSettings
                    .<StringValue, StringValue>newBuilder()
                    .setMethodDescriptor(ECHO_METHOD)
                    .setRequestMutator(request -> StringValue.of(request.getValue() + "!"))
                    .setParamsExtractor(request -> Map.of("message", request.getValue()))
                    .setShouldAwaitTrailers(true)
                    .build();
            UnaryCallable<StringValue, StringValue> callable = GrpcRawCallableFactory
                    .createUnaryCallable(callSettings, Set.of(StatusCode.Code.UNAVAILABLE));
            GrpcCallContext context = GrpcCallContext
                    .of(fixture.channel(), CallOptions.DEFAULT)
                    .withTimeoutDuration(Duration.ofSeconds(5))
                    .withExtraHeaders(Map.of("x-test-header", List.of("alpha", "beta")));

            StringValue response = callable.call(StringValue.of("hello"), context);

            assertThat(response.getValue()).isEqualTo("HELLO");
            assertThat(receivedHeaders.get().getAll(TEST_HEADER)).containsExactly("alpha", "beta");
            assertThat(callSettings.shouldAwaitTrailers()).isTrue();
            assertThat(callSettings.getParamsExtractor().extract(StringValue.of("abc")))
                    .containsEntry("message", "abc");
            assertThat(callSettings.getRequestMutator().apply(StringValue.of("abc")).getValue()).isEqualTo("abc!");
            assertThat(callSettings.toBuilder().build()).isNotSameAs(callSettings);
        } finally {
            fixture.close();
        }
    }

    @Test
    void rawServerStreamingCallableCollectsResponses() throws Exception {
        InProcessFixture fixture = InProcessFixture.start(expandService());
        try {
            ServerStreamingCallable<StringValue, StringValue> callable = GrpcRawCallableFactory
                    .createServerStreamingCallable(GrpcCallSettings.create(EXPAND_METHOD), Set.of());
            GrpcCallContext context = GrpcCallContext
                    .of(fixture.channel(), CallOptions.DEFAULT)
                    .withTimeoutDuration(Duration.ofSeconds(5));

            List<StringValue> responses = callable.all().call(StringValue.of("abc"), context);

            assertThat(responses).extracting(StringValue::getValue).containsExactly("a", "b", "c");
        } finally {
            fixture.close();
        }
    }

    @Test
    void rawClientStreamingCallableAggregatesClientMessages() throws Exception {
        InProcessFixture fixture = InProcessFixture.start(collectService());
        try {
            ClientStreamingCallable<StringValue, StringValue> callable = GrpcRawCallableFactory
                    .createClientStreamingCallable(GrpcCallSettings.create(COLLECT_METHOD), Set.of());
            GrpcCallContext context = GrpcCallContext
                    .of(fixture.channel(), CallOptions.DEFAULT)
                    .withTimeoutDuration(Duration.ofSeconds(5));
            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<StringValue> response = new AtomicReference<>();
            AtomicReference<Throwable> error = new AtomicReference<>();

            ApiStreamObserver<StringValue> requests = callable.clientStreamingCall(new ApiStreamObserver<>() {
                @Override
                public void onNext(StringValue value) {
                    response.set(value);
                }

                @Override
                public void onError(Throwable throwable) {
                    error.set(throwable);
                    completed.countDown();
                }

                @Override
                public void onCompleted() {
                    completed.countDown();
                }
            }, context);
            requests.onNext(StringValue.of("one"));
            requests.onNext(StringValue.of("two"));
            requests.onNext(StringValue.of("three"));
            requests.onCompleted();

            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(error.get()).isNull();
            assertThat(response.get().getValue()).isEqualTo("one,two,three");
        } finally {
            fixture.close();
        }
    }

    @Test
    void rawUnaryCallableTranslatesGrpcFailuresToApiExceptions() throws Exception {
        ServerServiceDefinition service = ServerServiceDefinition
                .builder(SERVICE_NAME)
                .addMethod(ECHO_METHOD, ServerCalls.asyncUnaryCall((request, observer) -> observer.onError(
                        Status.INVALID_ARGUMENT.withDescription("bad input").asRuntimeException())))
                .build();
        InProcessFixture fixture = InProcessFixture.start(service);

        try {
            UnaryCallable<StringValue, StringValue> callable = GrpcRawCallableFactory
                    .createUnaryCallable(GrpcCallSettings.create(ECHO_METHOD), Set.of());
            GrpcCallContext context = GrpcCallContext
                    .of(fixture.channel(), CallOptions.DEFAULT)
                    .withTimeoutDuration(Duration.ofSeconds(5));

            assertThatThrownBy(() -> callable.call(StringValue.of("bad"), context))
                    .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(
                            exception.getStatusCode().getCode()).isEqualTo(StatusCode.Code.INVALID_ARGUMENT))
                    .hasMessageContaining("bad input");
        } finally {
            fixture.close();
        }
    }

    @Test
    void transportChannelExposesGrpcChannelAndLifecycle() throws Exception {
        InProcessFixture fixture = InProcessFixture.start(echoService());
        GrpcTransportChannel transportChannel = GrpcTransportChannel.create(fixture.channel());

        assertThat(GrpcTransportChannel.getGrpcTransportName()).isEqualTo("grpc");
        assertThat(transportChannel.getTransportName()).isEqualTo("grpc");
        assertThat(transportChannel.isDirectPath()).isFalse();
        assertThat(transportChannel.getChannel()).isSameAs(fixture.channel());
        assertThat(transportChannel.getEmptyCallContext().getChannel()).isNull();

        transportChannel.shutdownNow();
        assertThat(transportChannel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(transportChannel.isShutdown()).isTrue();
        assertThat(transportChannel.isTerminated()).isTrue();
        fixture.closeServerOnly();
    }

    @Test
    void callContextIsImmutableAndMergesGrpcSpecificOptions() throws Exception {
        InProcessFixture fixture = InProcessFixture.start(echoService());
        try {
            ApiCallContext.Key<String> customKey = ApiCallContext.Key.create("custom-option");
            RetrySettings retrySettings = RetrySettings
                    .newBuilder()
                    .setMaxAttempts(3)
                    .setTotalTimeoutDuration(Duration.ofSeconds(10))
                    .setInitialRetryDelayDuration(Duration.ofMillis(10))
                    .setRetryDelayMultiplier(1.0)
                    .setMaxRetryDelayDuration(Duration.ofMillis(20))
                    .setInitialRpcTimeoutDuration(Duration.ofSeconds(2))
                    .setRpcTimeoutMultiplier(1.0)
                    .setMaxRpcTimeoutDuration(Duration.ofSeconds(2))
                    .build();
            GrpcCallContext base = GrpcCallContext
                    .createDefault()
                    .withChannel(fixture.channel())
                    .withTimeoutDuration(Duration.ofSeconds(3))
                    .withStreamWaitTimeoutDuration(Duration.ofSeconds(4))
                    .withStreamIdleTimeoutDuration(Duration.ofSeconds(5))
                    .withChannelAffinity(7)
                    .withRetrySettings(retrySettings)
                    .withRetryableCodes(Set.of(StatusCode.Code.UNAVAILABLE))
                    .withOption(customKey, "base")
                    .withExtraHeaders(Map.of("x-base", List.of("one")));
            GrpcCallContext overriding = GrpcCallContext
                    .createDefault()
                    .withTimeoutDuration(Duration.ofSeconds(8))
                    .withOption(customKey, "override")
                    .withExtraHeaders(Map.of("x-override", List.of("two")));

            GrpcCallContext merged = (GrpcCallContext) base.merge(overriding);

            assertThat(base.getTimeoutDuration()).isEqualTo(Duration.ofSeconds(3));
            assertThat(merged.getChannel()).isSameAs(fixture.channel());
            assertThat(merged.getTimeoutDuration()).isEqualTo(Duration.ofSeconds(8));
            assertThat(merged.getStreamWaitTimeoutDuration()).isEqualTo(Duration.ofSeconds(4));
            assertThat(merged.getStreamIdleTimeoutDuration()).isEqualTo(Duration.ofSeconds(5));
            assertThat(merged.getChannelAffinity()).isEqualTo(7);
            assertThat(merged.getRetrySettings()).isEqualTo(retrySettings);
            assertThat(merged.getRetryableCodes()).containsExactly(StatusCode.Code.UNAVAILABLE);
            assertThat(merged.getOption(customKey)).isEqualTo("override");
            assertThat(merged.getExtraHeaders()).containsEntry("x-base", List.of("one"));
            assertThat(merged.getExtraHeaders()).containsEntry("x-override", List.of("two"));
            assertThat(base.nullToSelf(null)).isSameAs(base);
        } finally {
            fixture.close();
        }
    }

    @Test
    void channelPoolSettingsSupportStaticAndDynamicSizing() {
        ChannelPoolSettings staticSettings = ChannelPoolSettings.staticallySized(3);
        assertThat(staticSettings.getInitialChannelCount()).isEqualTo(3);
        assertThat(staticSettings.getMinChannelCount()).isEqualTo(3);
        assertThat(staticSettings.getMaxChannelCount()).isEqualTo(3);

        ChannelPoolSettings dynamicSettings = ChannelPoolSettings
                .builder()
                .setInitialChannelCount(2)
                .setMinChannelCount(1)
                .setMaxChannelCount(4)
                .setMinRpcsPerChannel(5)
                .setMaxRpcsPerChannel(20)
                .setPreemptiveRefreshEnabled(true)
                .build();

        assertThat(dynamicSettings.getInitialChannelCount()).isEqualTo(2);
        assertThat(dynamicSettings.getMinRpcsPerChannel()).isEqualTo(5);
        assertThat(dynamicSettings.getMaxRpcsPerChannel()).isEqualTo(20);
        assertThat(dynamicSettings.isPreemptiveRefreshEnabled()).isTrue();
        assertThat(dynamicSettings.toBuilder().setMaxChannelCount(5).build().getMaxChannelCount()).isEqualTo(5);
        assertThatThrownBy(() -> ChannelPoolSettings.staticallySized(0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void instantiatingGrpcChannelProviderPreservesBuilderOptions() {
        ChannelPoolSettings poolSettings = ChannelPoolSettings.staticallySized(2);
        InstantiatingGrpcChannelProvider provider = InstantiatingGrpcChannelProvider
                .newBuilder()
                .setEndpoint("localhost:443")
                .setMtlsEndpoint("mtls.localhost:443")
                .setKeepAliveTimeDuration(Duration.ofSeconds(30))
                .setKeepAliveTimeoutDuration(Duration.ofSeconds(5))
                .setKeepAliveWithoutCalls(true)
                .setMaxInboundMetadataSize(16 * 1024)
                .setChannelPoolSettings(poolSettings)
                .build();

        assertThat(provider.getTransportName()).isEqualTo(GrpcTransportChannel.getGrpcTransportName());
        assertThat(provider.getEndpoint()).isEqualTo("localhost:443");
        assertThat(provider.getKeepAliveTimeDuration()).isEqualTo(Duration.ofSeconds(30));
        assertThat(provider.getKeepAliveTimeoutDuration()).isEqualTo(Duration.ofSeconds(5));
        assertThat(provider.getKeepAliveWithoutCalls()).isTrue();
        assertThat(provider.getMaxInboundMetadataSize()).isEqualTo(16 * 1024);
        assertThat(provider.getChannelPoolSettings()).isEqualTo(poolSettings);
        assertThat(provider.acceptsPoolSize()).isTrue();
        assertThat(provider.withPoolSize(3)).isInstanceOf(InstantiatingGrpcChannelProvider.class);
        assertThat(provider.withEndpoint("example.com:443")).isInstanceOf(InstantiatingGrpcChannelProvider.class);
        assertThat(provider.withMtlsEndpoint("mtls.example.com:443"))
                .isInstanceOf(InstantiatingGrpcChannelProvider.class);
        assertThat(provider.withHeaders(Map.of("x-client", "test")))
                .isInstanceOf(InstantiatingGrpcChannelProvider.class);
        assertThat(provider.toBuilder().getEndpoint()).isEqualTo("localhost:443");
    }

    @Test
    void grpcStatusCodesMapTransportCodesToGaxCodes() {
        assertThat(GrpcStatusCode.of(Status.Code.OK).getCode()).isEqualTo(StatusCode.Code.OK);
        assertThat(GrpcStatusCode.of(Status.Code.NOT_FOUND).getCode()).isEqualTo(StatusCode.Code.NOT_FOUND);
        assertThat(GrpcStatusCode.of(Status.Code.PERMISSION_DENIED).getCode())
                .isEqualTo(StatusCode.Code.PERMISSION_DENIED);
        assertThat(GrpcStatusCode.of(Status.Code.UNAVAILABLE).getCode()).isEqualTo(StatusCode.Code.UNAVAILABLE);
        assertThat(GrpcStatusCode.of(Status.Code.UNKNOWN).getCode()).isEqualTo(StatusCode.Code.UNKNOWN);
        assertThat(GrpcStatusCode.of(Status.Code.NOT_FOUND).getTransportCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void metadataHandlersAndHeaderInterceptorExposeGrpcMetadata() {
        Metadata headers = new Metadata();
        Metadata trailers = new Metadata();
        headers.put(TEST_HEADER, "header-value");
        trailers.put(TEST_HEADER, "trailer-value");

        GrpcResponseMetadata responseMetadata = new GrpcResponseMetadata();
        responseMetadata.onHeaders(headers);
        responseMetadata.onTrailers(trailers);

        assertThat(responseMetadata.getMetadata().get(TEST_HEADER)).isEqualTo("header-value");
        assertThat(responseMetadata.getTrailingMetadata().get(TEST_HEADER)).isEqualTo("trailer-value");
        assertThat(responseMetadata.createContextWithHandlers().getCallOptions()).isNotNull();
        assertThat(responseMetadata.addHandlers(GrpcCallContext.createDefault()).getCallOptions()).isNotNull();

        GrpcHeaderInterceptor headerInterceptor = new GrpcHeaderInterceptor(Map.of(
                "user-agent", "reachability-test",
                "x-client", "metadata"));
        assertThat(headerInterceptor.getUserAgentHeader()).contains("reachability-test");
    }

    @Test
    void gaxGrpcPropertiesExposeVersionedHeaderTokens() {
        assertThat(GaxGrpcProperties.getGrpcVersion()).isNotBlank();
        assertThat(GaxGrpcProperties.getGrpcTokenName()).isEqualTo("grpc");
        assertThat(GaxGrpcProperties.getGaxGrpcVersion()).isNotBlank();
        assertThat(GaxGrpcProperties.getDefaultApiClientHeaderPattern()
                        .matcher("gl-java/21 gapic/1.0.0 gax/2.0.0 grpc/1.0.0")
                        .matches())
                .isTrue();
    }

    private static ServerServiceDefinition echoService() {
        return ServerServiceDefinition
                .builder(SERVICE_NAME)
                .addMethod(ECHO_METHOD, ServerCalls.asyncUnaryCall((request, observer) -> {
                    observer.onNext(StringValue.of(request.getValue().toUpperCase(Locale.ROOT)));
                    observer.onCompleted();
                }))
                .build();
    }

    private static ServerServiceDefinition expandService() {
        return ServerServiceDefinition
                .builder(SERVICE_NAME)
                .addMethod(EXPAND_METHOD, ServerCalls.asyncServerStreamingCall((request, observer) -> {
                    for (int index = 0; index < request.getValue().length(); index++) {
                        observer.onNext(StringValue.of(String.valueOf(request.getValue().charAt(index))));
                    }
                    observer.onCompleted();
                }))
                .build();
    }

    private static ServerServiceDefinition collectService() {
        return ServerServiceDefinition
                .builder(SERVICE_NAME)
                .addMethod(COLLECT_METHOD, ServerCalls.asyncClientStreamingCall(observer -> new StreamObserver<>() {
                    private final StringBuilder response = new StringBuilder();

                    @Override
                    public void onNext(StringValue value) {
                        if (!response.isEmpty()) {
                            response.append(',');
                        }
                        response.append(value.getValue());
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        observer.onError(throwable);
                    }

                    @Override
                    public void onCompleted() {
                        observer.onNext(StringValue.of(response.toString()));
                        observer.onCompleted();
                    }
                }))
                .build();
    }

    private static ServerInterceptor captureHeaders(AtomicReference<Metadata> receivedHeaders) {
        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {
                receivedHeaders.set(headers);
                return next.startCall(call, headers);
            }
        };
    }

    private static final class InProcessFixture implements AutoCloseable {
        private final Server server;
        private final ManagedChannel channel;

        private InProcessFixture(Server server, ManagedChannel channel) {
            this.server = server;
            this.channel = channel;
        }

        private static InProcessFixture start(ServerServiceDefinition service) throws IOException {
            String serverName = InProcessServerBuilder.generateName();
            Server server = InProcessServerBuilder
                    .forName(serverName)
                    .directExecutor()
                    .addService(service)
                    .build()
                    .start();
            ManagedChannel channel = InProcessChannelBuilder
                    .forName(serverName)
                    .directExecutor()
                    .build();
            return new InProcessFixture(server, channel);
        }

        private ManagedChannel channel() {
            return channel;
        }

        private void closeServerOnly() throws InterruptedException {
            server.shutdownNow();
            assertThat(server.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        @Override
        public void close() throws InterruptedException {
            channel.shutdownNow();
            assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            closeServerOnly();
        }
    }
}
