/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.BinaryLog;
import io.grpc.ClientInterceptor;
import io.grpc.CompressorRegistry;
import io.grpc.DecompressorRegistry;
import io.grpc.ForwardingChannelBuilder;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.NameResolver;
import io.grpc.ProxyDetector;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Exercises forwarding configuration as a user would wrap a transport-specific channel builder. */
public class GrpcApiForwardingBuilderTest {
    @Test
    void forwardingBuilderPassesEverySupportedConfigurationToItsDelegate() {
        RecordingBuilder delegate = new RecordingBuilder();
        Wrapper builder = new Wrapper(delegate);
        Executor executor = Runnable::run;
        ClientInterceptor interceptor = new ClientInterceptor() {
            @Override
            public <ReqT, RespT> io.grpc.ClientCall<ReqT, RespT> interceptCall(
                    io.grpc.MethodDescriptor<ReqT, RespT> method, io.grpc.CallOptions options,
                    io.grpc.Channel channel) {
                return channel.newCall(method, options);
            }
        };
        NameResolver.Factory resolverFactory = new NameResolver.Factory() {
            @Override public String getDefaultScheme() { return "test"; }
            @Override public NameResolver newNameResolver(java.net.URI uri, NameResolver.Args args) { return null; }
        };
        ProxyDetector proxyDetector = targetServerAddress -> null;
        BinaryLog binaryLog = new BinaryLog() {
            @Override public io.grpc.Channel wrapChannel(io.grpc.Channel channel) { return channel; }
            @Override public <ReqT, RespT> io.grpc.ServerMethodDefinition<ReqT, RespT> wrapMethodDefinition(io.grpc.ServerMethodDefinition<ReqT, RespT> methodDefinition) { return methodDefinition; }
            @Override public void close() {}
        };

        assertThat(builder.directExecutor()).isSameAs(builder);
        assertThat(builder.executor(executor)).isSameAs(builder);
        assertThat(builder.offloadExecutor(executor)).isSameAs(builder);
        assertThat(builder.blockingExecutor(executor)).isSameAs(builder);
        assertThat(builder.intercept(interceptor)).isSameAs(builder);
        assertThat(builder.intercept(Collections.singletonList(interceptor))).isSameAs(builder);
        assertThat(builder.userAgent("coverage-client").overrideAuthority("authority").usePlaintext()
                .useTransportSecurity().nameResolverFactory(resolverFactory).defaultLoadBalancingPolicy("pick_first")
                .enableFullStreamDecompression().decompressorRegistry(DecompressorRegistry.getDefaultInstance())
                .compressorRegistry(CompressorRegistry.getDefaultInstance()).idleTimeout(1, TimeUnit.SECONDS)
                .maxInboundMessageSize(1).maxInboundMetadataSize(1).keepAliveTime(1, TimeUnit.SECONDS)
                .keepAliveTimeout(1, TimeUnit.SECONDS).keepAliveWithoutCalls(true).maxRetryAttempts(2)
                .maxHedgedAttempts(2).retryBufferSize(2).perRpcBufferLimit(1).disableRetry().enableRetry()
                .setBinaryLog(binaryLog).maxTraceEvents(2).proxyDetector(proxyDetector)
                .defaultServiceConfig(Collections.singletonMap("loadBalancingPolicy", "pick_first"))
                .disableServiceConfigLookUp()).isSameAs(builder);
        assertThat(builder.build()).isSameAs(delegate.channel);
        assertThat(builder.toString()).contains("delegate");
        assertThat(delegate.calls).containsAll(Arrays.asList("directExecutor", "executor", "offloadExecutor",
                "blockingExecutor", "intercept-array", "intercept-list", "userAgent", "overrideAuthority",
                "usePlaintext", "useTransportSecurity", "nameResolverFactory", "defaultLoadBalancingPolicy",
                "enableFullStreamDecompression", "decompressorRegistry", "compressorRegistry", "idleTimeout",
                "maxInboundMessageSize", "maxInboundMetadataSize", "keepAliveTime", "keepAliveTimeout",
                "keepAliveWithoutCalls", "maxRetryAttempts", "maxHedgedAttempts", "retryBufferSize",
                "perRpcBufferLimit", "disableRetry", "enableRetry", "setBinaryLog", "maxTraceEvents",
                "proxyDetector", "defaultServiceConfig", "disableServiceConfigLookUp", "build"));
        assertThatThrownBy(() -> ForwardingChannelBuilder.forAddress("localhost", 1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ForwardingChannelBuilder.forTarget("dns:///localhost"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static final class Wrapper extends ForwardingChannelBuilder<Wrapper> {
        private final ManagedChannelBuilder<?> delegate;
        Wrapper(ManagedChannelBuilder<?> delegate) { this.delegate = delegate; }
        @Override protected ManagedChannelBuilder<?> delegate() { return delegate; }
    }

    private static final class RecordingBuilder extends ManagedChannelBuilder<RecordingBuilder> {
        final Set<String> calls = new LinkedHashSet<>();
        final ManagedChannel channel = new ManagedChannel() {
            @Override public ManagedChannel shutdown() { return this; }
            @Override public boolean isShutdown() { return false; }
            @Override public boolean isTerminated() { return false; }
            @Override public ManagedChannel shutdownNow() { return this; }
            @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
            @Override public String authority() { return "coverage"; }
            @Override public <ReqT, RespT> io.grpc.ClientCall<ReqT, RespT> newCall(io.grpc.MethodDescriptor<ReqT, RespT> method, io.grpc.CallOptions options) { return null; }
        };
        private RecordingBuilder called(String name) { calls.add(name); return this; }
        @Override public RecordingBuilder directExecutor() { return called("directExecutor"); }
        @Override public RecordingBuilder executor(Executor value) { return called("executor"); }
        @Override public RecordingBuilder offloadExecutor(Executor value) { return called("offloadExecutor"); }
        @Override public RecordingBuilder blockingExecutor(Executor value) { return called("blockingExecutor"); }
        @Override public RecordingBuilder intercept(List<ClientInterceptor> value) { return called("intercept-list"); }
        @Override public RecordingBuilder intercept(ClientInterceptor... value) { return called("intercept-array"); }
        @Override public RecordingBuilder userAgent(String value) { return called("userAgent"); }
        @Override public RecordingBuilder overrideAuthority(String value) { return called("overrideAuthority"); }
        @Override public RecordingBuilder usePlaintext() { return called("usePlaintext"); }
        @Override public RecordingBuilder useTransportSecurity() { return called("useTransportSecurity"); }
        @Override public RecordingBuilder nameResolverFactory(NameResolver.Factory value) { return called("nameResolverFactory"); }
        @Override public RecordingBuilder defaultLoadBalancingPolicy(String value) { return called("defaultLoadBalancingPolicy"); }
        @Override public RecordingBuilder enableFullStreamDecompression() { return called("enableFullStreamDecompression"); }
        @Override public RecordingBuilder decompressorRegistry(DecompressorRegistry value) { return called("decompressorRegistry"); }
        @Override public RecordingBuilder compressorRegistry(CompressorRegistry value) { return called("compressorRegistry"); }
        @Override public RecordingBuilder idleTimeout(long value, TimeUnit unit) { return called("idleTimeout"); }
        @Override public RecordingBuilder maxInboundMessageSize(int value) { return called("maxInboundMessageSize"); }
        @Override public RecordingBuilder maxInboundMetadataSize(int value) { return called("maxInboundMetadataSize"); }
        @Override public RecordingBuilder keepAliveTime(long value, TimeUnit unit) { return called("keepAliveTime"); }
        @Override public RecordingBuilder keepAliveTimeout(long value, TimeUnit unit) { return called("keepAliveTimeout"); }
        @Override public RecordingBuilder keepAliveWithoutCalls(boolean value) { return called("keepAliveWithoutCalls"); }
        @Override public RecordingBuilder maxRetryAttempts(int value) { return called("maxRetryAttempts"); }
        @Override public RecordingBuilder maxHedgedAttempts(int value) { return called("maxHedgedAttempts"); }
        @Override public RecordingBuilder retryBufferSize(long value) { return called("retryBufferSize"); }
        @Override public RecordingBuilder perRpcBufferLimit(long value) { return called("perRpcBufferLimit"); }
        @Override public RecordingBuilder disableRetry() { return called("disableRetry"); }
        @Override public RecordingBuilder enableRetry() { return called("enableRetry"); }
        @Override public RecordingBuilder setBinaryLog(BinaryLog value) { return called("setBinaryLog"); }
        @Override public RecordingBuilder maxTraceEvents(int value) { return called("maxTraceEvents"); }
        @Override public RecordingBuilder proxyDetector(ProxyDetector value) { return called("proxyDetector"); }
        @Override public RecordingBuilder defaultServiceConfig(Map<String, ?> value) { return called("defaultServiceConfig"); }
        @Override public RecordingBuilder disableServiceConfigLookUp() { return called("disableServiceConfigLookUp"); }
        @Override public ManagedChannel build() { calls.add("build"); return channel; }
    }
}
