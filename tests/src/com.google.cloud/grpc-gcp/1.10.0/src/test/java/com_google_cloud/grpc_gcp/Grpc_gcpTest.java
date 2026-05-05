/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud.grpc_gcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.api.core.ApiFunction;
import com.google.cloud.grpc.GcpManagedChannel;
import com.google.cloud.grpc.GcpManagedChannelBuilder;
import com.google.cloud.grpc.GcpManagedChannelOptions;
import com.google.cloud.grpc.GcpManagedChannelOptions.ChannelPickStrategy;
import com.google.cloud.grpc.GcpManagedChannelOptions.GcpChannelPoolOptions;
import com.google.cloud.grpc.GcpManagedChannelOptions.GcpMetricsOptions;
import com.google.cloud.grpc.GcpManagedChannelOptions.GcpResiliencyOptions;
import com.google.cloud.grpc.GcpMultiEndpointChannel;
import com.google.cloud.grpc.GcpMultiEndpointOptions;
import com.google.cloud.grpc.fallback.GcpFallbackChannel;
import com.google.cloud.grpc.fallback.GcpFallbackChannelOptions;
import com.google.cloud.grpc.fallback.GcpFallbackOpenTelemetry;
import com.google.cloud.grpc.multiendpoint.MultiEndpoint;
import com.google.cloud.grpc.proto.AffinityConfig;
import com.google.cloud.grpc.proto.ApiConfig;
import com.google.cloud.grpc.proto.ChannelPoolConfig;
import com.google.cloud.grpc.proto.MethodConfig;
import com.google.protobuf.ByteString;
import com.google.protobuf.util.JsonFormat;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.CompressorRegistry;
import io.grpc.ConnectivityState;
import io.grpc.Context;
import io.grpc.DecompressorRegistry;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.NameResolver;
import io.grpc.Status;
import io.opencensus.metrics.LabelKey;
import io.opencensus.metrics.LabelValue;
import io.opencensus.metrics.MetricRegistry;
import io.opencensus.metrics.Metrics;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

public class Grpc_gcpTest {
    private static final MethodDescriptor<String, String> METHOD = MethodDescriptor.<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(MethodDescriptor.generateFullMethodName("test.Service", "Unary"))
            .setRequestMarshaller(new StringMarshaller())
            .setResponseMarshaller(new StringMarshaller())
            .build();

    @Test
    void managedChannelOptionsRetainConfiguredValuesAndCanBeCopied() {
        MetricRegistry metricRegistry = Metrics.getMetricRegistry();
        LabelKey labelKey = LabelKey.create("location", "Cloud location");
        LabelValue labelValue = LabelValue.create("test-region");
        Meter meter = OpenTelemetry.noop().getMeter("grpc-gcp-test");
        GcpChannelPoolOptions poolOptions = GcpChannelPoolOptions.newBuilder()
                .setMaxSize(5)
                .setMinSize(1)
                .setInitSize(2)
                .setDynamicScaling(3, 9, Duration.ofSeconds(4))
                .setConcurrentStreamsLowWatermark(17)
                .setUseRoundRobinOnBind(true)
                .setAffinityKeyLifetime(Duration.ofMinutes(10))
                .setChannelPickStrategy(ChannelPickStrategy.LINEAR_SCAN)
                .build();
        GcpMetricsOptions metricsOptions = GcpMetricsOptions.newBuilder()
                .withMetricRegistry(metricRegistry)
                .withLabels(List.of(labelKey), List.of(labelValue))
                .withNamePrefix("custom_prefix")
                .withOpenTelemetryMeter(meter)
                .withOtelLabels(List.of("service"), List.of("test-service"))
                .build();
        GcpResiliencyOptions resiliencyOptions = GcpResiliencyOptions.newBuilder()
                .setNotReadyFallback(true)
                .withUnresponsiveConnectionDetection(250, 4)
                .build();

        GcpManagedChannelOptions options = GcpManagedChannelOptions.newBuilder()
                .withChannelPoolOptions(poolOptions)
                .withMetricsOptions(metricsOptions)
                .withResiliencyOptions(resiliencyOptions)
                .build();
        GcpManagedChannelOptions copied = GcpManagedChannelOptions.newBuilder(options).build();

        assertThat(copied.getChannelPoolOptions().getMaxSize()).isEqualTo(5);
        assertThat(copied.getChannelPoolOptions().getMinSize()).isEqualTo(1);
        assertThat(copied.getChannelPoolOptions().getInitSize()).isEqualTo(2);
        assertThat(copied.getChannelPoolOptions().getMinRpcPerChannel()).isEqualTo(3);
        assertThat(copied.getChannelPoolOptions().getMaxRpcPerChannel()).isEqualTo(9);
        assertThat(copied.getChannelPoolOptions().getScaleDownInterval()).isEqualTo(Duration.ofSeconds(4));
        assertThat(copied.getChannelPoolOptions().getConcurrentStreamsLowWatermark()).isEqualTo(17);
        assertThat(copied.getChannelPoolOptions().isUseRoundRobinOnBind()).isTrue();
        assertThat(copied.getChannelPoolOptions().getAffinityKeyLifetime()).isEqualTo(Duration.ofMinutes(10));
        assertThat(copied.getChannelPoolOptions().getCleanupInterval()).isEqualTo(Duration.ofMinutes(1));
        assertThat(copied.getChannelPoolOptions().getChannelPickStrategy()).isEqualTo(ChannelPickStrategy.LINEAR_SCAN);
        assertThat(copied.getMetricsOptions().getMetricRegistry()).isSameAs(metricRegistry);
        assertThat(copied.getMetricsOptions().getLabelKeys()).containsExactly(labelKey);
        assertThat(copied.getMetricsOptions().getLabelValues()).containsExactly(labelValue);
        assertThat(copied.getMetricsOptions().getNamePrefix()).isEqualTo("custom_prefix");
        assertThat(copied.getMetricsOptions().getOpenTelemetryMeter()).isSameAs(meter);
        assertThat(copied.getMetricsOptions().getOtelLabelKeys()).containsExactly("service");
        assertThat(copied.getMetricsOptions().getOtelLabelValues()).containsExactly("test-service");
        assertThat(copied.getResiliencyOptions().isNotReadyFallbackEnabled()).isTrue();
        assertThat(copied.getResiliencyOptions().isUnresponsiveDetectionEnabled()).isTrue();
        assertThat(copied.getResiliencyOptions().getUnresponsiveDetectionMs()).isEqualTo(250);
        assertThat(copied.getResiliencyOptions().getUnresponsiveDetectionDroppedCount()).isEqualTo(4);
        assertThat(copied.toString()).contains("channelPoolOptions", "metricsOptions", "resiliencyOptions");
    }

    @Test
    void optionBuildersValidatePoolAndEndpointInputs() {
        assertThatThrownBy(() -> GcpChannelPoolOptions.newBuilder().setMaxSize(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> GcpChannelPoolOptions.newBuilder().setMinSize(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 or positive");
        assertThatThrownBy(() -> GcpChannelPoolOptions.newBuilder().setDynamicScaling(1, 2, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Scale down interval");
        assertThatThrownBy(() -> GcpChannelPoolOptions.newBuilder().setAffinityKeyLifetime(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("may not be negative");
        assertThatThrownBy(() -> GcpMultiEndpointOptions.newBuilder(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one endpoint");
        assertThatThrownBy(() -> GcpMultiEndpointOptions.newBuilder(List.of("primary", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No empty endpoints");
    }

    @Test
    void protoConfigurationSupportsBuildersJsonAndParsing() throws Exception {
        ApiConfig config = ApiConfig.newBuilder()
                .setChannelPool(ChannelPoolConfig.newBuilder()
                        .setMaxSize(4)
                        .setIdleTimeout(30)
                        .setMaxConcurrentStreamsLowWatermark(42))
                .addMethod(MethodConfig.newBuilder()
                        .addName("/test.Service/Unary")
                        .setAffinity(AffinityConfig.newBuilder()
                                .setCommand(AffinityConfig.Command.BIND)
                                .setAffinityKey("tenant_id")))
                .build();

        byte[] serialized = config.toByteArray();
        ApiConfig fromBytes = ApiConfig.parseFrom(serialized);
        ApiConfig fromByteString = ApiConfig.parseFrom(ByteString.copyFrom(serialized));
        String json = JsonFormat.printer().print(config);
        ApiConfig.Builder jsonBuilder = ApiConfig.newBuilder();
        JsonFormat.parser().merge(json, jsonBuilder);
        ByteArrayOutputStream delimitedMethod = new ByteArrayOutputStream();
        config.getMethod(0).writeDelimitedTo(delimitedMethod);

        assertThat(fromBytes).isEqualTo(config);
        assertThat(fromByteString.getChannelPool().getMaxSize()).isEqualTo(4);
        assertThat(jsonBuilder.build().getMethod(0).getAffinity().getCommand()).isEqualTo(AffinityConfig.Command.BIND);
        assertThat(AffinityConfig.Command.forNumber(AffinityConfig.Command.UNBIND_VALUE))
                .isEqualTo(AffinityConfig.Command.UNBIND);
        assertThat(MethodConfig.parseDelimitedFrom(new ByteArrayInputStream(delimitedMethod.toByteArray())))
                .isEqualTo(config.getMethod(0));
    }

    @Test
    void managedChannelBuilderLoadsApiConfigFromJsonStringAndFile() throws Exception {
        String json = """
                {
                  "channelPool": {
                    "maxSize": 3,
                    "maxConcurrentStreamsLowWatermark": 7
                  },
                  "method": [{
                    "name": ["/test.Service/Unary"],
                    "affinity": {
                      "command": "BIND",
                      "affinityKey": "tenant_id"
                    }
                  }]
                }
                """;
        GcpManagedChannel fromString = null;
        GcpManagedChannel fromFile = null;
        try {
            fromString = (GcpManagedChannel) GcpManagedChannelBuilder
                    .forDelegateBuilder(new TestManagedChannelBuilder("string-config-authority"))
                    .withApiConfigJsonString(json)
                    .setPoolSize(2)
                    .build();
            File configFile = Files.writeString(Files.createTempFile("grpc-gcp", ".json"), json, StandardCharsets.UTF_8)
                    .toFile();
            fromFile = (GcpManagedChannel) GcpManagedChannelBuilder
                    .forDelegateBuilder(new TestManagedChannelBuilder("file-config-authority"))
                    .withApiConfigJsonFile(configFile)
                    .build();

            assertThat(fromString.getMaxSize()).isEqualTo(2);
            assertThat(fromString.getStreamsLowWatermark()).isEqualTo(7);
            assertThat(fromString.authority()).isEqualTo("string-config-authority");
            assertThat(fromString.isShutdown()).isFalse();
            assertThat(fromFile.getMaxSize()).isEqualTo(3);
            assertThat(fromFile.getStreamsLowWatermark()).isEqualTo(7);
            assertThat(CallOptions.DEFAULT.withOption(GcpManagedChannel.AFFINITY_KEY, "tenant-a")
                    .getOption(GcpManagedChannel.AFFINITY_KEY)).isEqualTo("tenant-a");
        } finally {
            shutdown(fromString);
            shutdown(fromFile);
        }
    }

    @Test
    void multiEndpointOptionsCanBeCopiedAndCoreEndpointSelectionTracksSwitches() {
        ApiFunction<ManagedChannelBuilder<?>, ManagedChannelBuilder<?>> configurator = builder -> builder;
        GcpMultiEndpointOptions options = GcpMultiEndpointOptions.newBuilder(List.of("first.example:443"))
                .withName("test-multi-endpoint")
                .withEndpoints(List.of("first.example:443", "second.example:443"))
                .withChannelConfigurator(configurator)
                .withChannelCredentials(InsecureChannelCredentials.create())
                .withRecoveryTimeout(Duration.ofMillis(100))
                .withSwitchingDelay(Duration.ZERO)
                .build();
        GcpMultiEndpointOptions copy = GcpMultiEndpointOptions.newBuilder(options).build();

        assertThat(copy.getName()).isEqualTo("test-multi-endpoint");
        assertThat(copy.getEndpoints()).containsExactly("first.example:443", "second.example:443");
        assertThat(copy.getChannelConfigurator()).isSameAs(configurator);
        assertThat(copy.getChannelCredentials()).isNotNull();
        assertThat(copy.getRecoveryTimeout()).isEqualTo(Duration.ofMillis(100));
        assertThat(copy.getSwitchingDelay()).isEqualTo(Duration.ZERO);

        MultiEndpoint multiEndpoint = new MultiEndpoint.Builder(List.of("primary", "secondary"))
                .withRecoveryTimeout(Duration.ZERO)
                .withSwitchingDelay(Duration.ZERO)
                .build();
        assertThat(multiEndpoint.getEndpoints()).containsExactly("primary", "secondary");
        assertThat(multiEndpoint.getCurrentId()).isEqualTo("primary");

        multiEndpoint.setEndpointAvailable("primary", true);
        multiEndpoint.setEndpointAvailable("secondary", true);
        multiEndpoint.setEndpointAvailable("primary", false);
        assertThat(multiEndpoint.getCurrentId()).isEqualTo("secondary");
        assertThat(multiEndpoint.getFallbackCnt()).isEqualTo(1);

        multiEndpoint.setEndpointAvailable("primary", true);
        assertThat(multiEndpoint.getCurrentId()).isEqualTo("primary");
        assertThat(multiEndpoint.getRecoverCnt()).isEqualTo(1);

        multiEndpoint.setEndpoints(List.of("replacement"));
        assertThat(multiEndpoint.getEndpoints()).containsExactly("replacement");
        assertThat(multiEndpoint.getCurrentId()).isEqualTo("replacement");
        assertThat(multiEndpoint.getReplaceCnt()).isEqualTo(1);
        assertThat(multiEndpoint.toString()).contains("currentId='replacement'");
    }

    @Test
    void multiEndpointChannelRoutesCallsUsingDefaultCallOptionAndContextSelections() throws Exception {
        AtomicInteger builderIndex = new AtomicInteger();
        List<CountingManagedChannel> createdChannels = new ArrayList<>();
        ApiFunction<ManagedChannelBuilder<?>, ManagedChannelBuilder<?>> configurator = builder ->
                new TestManagedChannelBuilder("pool-" + builderIndex.incrementAndGet(), createdChannels);
        GcpManagedChannelOptions managedOptions = GcpManagedChannelOptions.newBuilder()
                .withChannelPoolOptions(GcpChannelPoolOptions.newBuilder()
                        .setMaxSize(1)
                        .setMinSize(0)
                        .setInitSize(0)
                        .build())
                .build();
        GcpMultiEndpointOptions defaultEndpoint = GcpMultiEndpointOptions.newBuilder(
                        List.of("default-primary", "default-backup"))
                .withName("default")
                .withChannelConfigurator(configurator)
                .withRecoveryTimeout(Duration.ZERO)
                .withSwitchingDelay(Duration.ZERO)
                .build();
        GcpMultiEndpointOptions analyticsEndpoint = GcpMultiEndpointOptions.newBuilder(List.of("analytics-primary"))
                .withName("analytics")
                .withChannelConfigurator(configurator)
                .withRecoveryTimeout(Duration.ZERO)
                .withSwitchingDelay(Duration.ZERO)
                .build();
        GcpMultiEndpointChannel channel = new GcpMultiEndpointChannel(
                List.of(defaultEndpoint, analyticsEndpoint), ApiConfig.getDefaultInstance(), managedOptions);
        try {
            assertThat(createdChannels).hasSize(3);
            assertThat(channel.authority()).isEqualTo("pool-1");
            assertThat(channel.authorityFor("default")).isEqualTo("pool-1");
            assertThat(channel.authorityFor("analytics")).isEqualTo("pool-3");
            assertThat(channel.authorityFor("missing")).isNull();

            channel.newCall(METHOD, CallOptions.DEFAULT);
            channel.newCall(METHOD, CallOptions.DEFAULT.withOption(GcpMultiEndpointChannel.ME_KEY, "analytics"));
            Context.current()
                    .withValue(GcpMultiEndpointChannel.ME_CONTEXT_KEY, "analytics")
                    .run(() -> channel.newCall(METHOD, CallOptions.DEFAULT));
            Context.current()
                    .withValue(GcpMultiEndpointChannel.ME_CONTEXT_KEY, "analytics")
                    .run(() -> channel.newCall(
                            METHOD, CallOptions.DEFAULT.withOption(GcpMultiEndpointChannel.ME_KEY, "default")));
            channel.newCall(METHOD, CallOptions.DEFAULT.withOption(GcpMultiEndpointChannel.ME_KEY, "unknown"));

            assertThat(createdChannels.get(0).newCallCount()).isEqualTo(3);
            assertThat(createdChannels.get(1).newCallCount()).isZero();
            assertThat(createdChannels.get(2).newCallCount()).isEqualTo(2);
        } finally {
            channel.shutdownNow();
            assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void fallbackChannelSwitchesToFallbackWhenPrimaryErrorRateThresholdIsReached() throws Exception {
        GcpFallbackChannelOptions options = GcpFallbackChannelOptions.newBuilder()
                .setEnableFallback(true)
                .setErrorRateThreshold(0.5f)
                .setErroneousStates(EnumSet.of(Status.Code.UNAVAILABLE))
                .setPeriod(Duration.ofMillis(100))
                .setMinFailedCalls(2)
                .setGcpFallbackOpenTelemetry(GcpFallbackOpenTelemetry.newBuilder().disableAllMetrics().build())
                .build();
        StatusManagedChannel primary = new StatusManagedChannel("primary-authority", Status.UNAVAILABLE);
        StatusManagedChannel fallback = new StatusManagedChannel("fallback-authority", Status.OK);
        GcpFallbackChannel channel = new GcpFallbackChannel(options, primary, fallback);
        try {
            startCall(channel);
            startCall(channel);

            assertThat(primary.newCallCount()).isEqualTo(2);
            assertThat(fallback.newCallCount()).isZero();

            waitUntil(channel::isInFallbackMode);
            assertThat(channel.authority()).isEqualTo("fallback-authority");

            startCall(channel);
            assertThat(fallback.newCallCount()).isEqualTo(1);
        } finally {
            channel.shutdownNow();
            assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void fallbackOptionsConfigureFallbackChannelAndDelegateLifecycle() throws Exception {
        GcpFallbackOpenTelemetry openTelemetry = GcpFallbackOpenTelemetry.newBuilder()
                .withSdk(OpenTelemetry.noop())
                .disableAllMetrics()
                .enableMetrics(List.of("grpc.gcp.fallback.current_channel"))
                .disableMetrics(List.of("grpc.gcp.fallback.error_rate"))
                .build();
        Set<Status.Code> erroneousStates = EnumSet.of(Status.Code.UNAVAILABLE, Status.Code.INTERNAL);
        GcpFallbackChannelOptions options = GcpFallbackChannelOptions.newBuilder()
                .setEnableFallback(false)
                .setErrorRateThreshold(0.75f)
                .setErroneousStates(erroneousStates)
                .setPeriod(Duration.ofMillis(100))
                .setMinFailedCalls(2)
                .setProbingFunction(channel -> channel.authority())
                .setPrimaryProbingInterval(Duration.ofSeconds(1))
                .setFallbackProbingInterval(Duration.ofSeconds(2))
                .setPrimaryChannelName("primary-channel")
                .setFallbackChannelName("fallback-channel")
                .setGcpFallbackOpenTelemetry(openTelemetry)
                .build();
        CountingManagedChannel primary = new CountingManagedChannel("primary-authority");
        CountingManagedChannel fallback = new CountingManagedChannel("fallback-authority");
        GcpFallbackChannel channel = new GcpFallbackChannel(options, primary, fallback);
        try {
            ClientCall<String, String> call = channel.newCall(METHOD, CallOptions.DEFAULT);

            assertThat(call).isNotNull();
            assertThat(channel.isInFallbackMode()).isFalse();
            assertThat(channel.authority()).isEqualTo("primary-authority");
            assertThat(primary.newCallCount()).isEqualTo(1);
            assertThat(fallback.newCallCount()).isZero();
            assertThat(options.isEnableFallback()).isFalse();
            assertThat(options.getErrorRateThreshold()).isEqualTo(0.75f);
            assertThat(options.getErroneousStates()).containsExactlyInAnyOrderElementsOf(erroneousStates);
            assertThat(options.getPeriod()).isEqualTo(Duration.ofMillis(100));
            assertThat(options.getMinFailedCalls()).isEqualTo(2);
            assertThat(options.getPrimaryProbingFunction().apply(primary)).isEqualTo("primary-authority");
            assertThat(options.getFallbackProbingFunction().apply(fallback)).isEqualTo("fallback-authority");
            assertThat(options.getPrimaryProbingInterval()).isEqualTo(Duration.ofSeconds(1));
            assertThat(options.getFallbackProbingInterval()).isEqualTo(Duration.ofSeconds(2));
            assertThat(options.getPrimaryChannelName()).isEqualTo("primary-channel");
            assertThat(options.getFallbackChannelName()).isEqualTo("fallback-channel");
            assertThat(options.getGcpOpenTelemetry()).isSameAs(openTelemetry);
        } finally {
            channel.shutdownNow();
            assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(primary.isShutdown()).isTrue();
        assertThat(fallback.isShutdown()).isTrue();
    }

    private static void shutdown(ManagedChannel channel) throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void startCall(ManagedChannel channel) {
        ClientCall<String, String> call = channel.newCall(METHOD, CallOptions.DEFAULT);
        call.start(new ClientCall.Listener<>() {
        }, new Metadata());
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static final class StringMarshaller implements MethodDescriptor.Marshaller<String> {
        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new IllegalStateException("Unable to parse test message", e);
            }
        }
    }

    private static final class TestManagedChannelBuilder extends ManagedChannelBuilder<TestManagedChannelBuilder> {
        private final String authority;
        private final List<CountingManagedChannel> createdChannels;

        private TestManagedChannelBuilder(String authority) {
            this(authority, new ArrayList<>());
        }

        private TestManagedChannelBuilder(String authority, List<CountingManagedChannel> createdChannels) {
            this.authority = authority;
            this.createdChannels = createdChannels;
        }

        @Override
        public TestManagedChannelBuilder directExecutor() {
            return this;
        }

        @Override
        public TestManagedChannelBuilder executor(Executor executor) {
            return this;
        }

        @Override
        public TestManagedChannelBuilder intercept(List<ClientInterceptor> interceptors) {
            return this;
        }

        @Override
        public TestManagedChannelBuilder intercept(ClientInterceptor... interceptors) {
            return this;
        }

        @Override
        public TestManagedChannelBuilder userAgent(String userAgent) {
            return this;
        }

        @Override
        public TestManagedChannelBuilder overrideAuthority(String authority) {
            return this;
        }

        @Override
        public TestManagedChannelBuilder nameResolverFactory(NameResolver.Factory resolverFactory) {
            return this;
        }

        @Override
        public TestManagedChannelBuilder decompressorRegistry(DecompressorRegistry registry) {
            return this;
        }

        @Override
        public TestManagedChannelBuilder compressorRegistry(CompressorRegistry registry) {
            return this;
        }

        @Override
        public TestManagedChannelBuilder idleTimeout(long value, TimeUnit unit) {
            return this;
        }

        @Override
        public ManagedChannel build() {
            CountingManagedChannel channel = new CountingManagedChannel(authority);
            createdChannels.add(channel);
            return channel;
        }
    }

    private static final class CountingManagedChannel extends ManagedChannel {
        private final String authority;
        private final AtomicInteger newCallCount = new AtomicInteger();
        private volatile boolean shutdown;

        private CountingManagedChannel(String authority) {
            this.authority = authority;
        }

        private int newCallCount() {
            return newCallCount.get();
        }

        @Override
        public ManagedChannel shutdown() {
            shutdown = true;
            return this;
        }

        @Override
        public ManagedChannel shutdownNow() {
            shutdown = true;
            return this;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
                MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions callOptions) {
            newCallCount.incrementAndGet();
            return new NoopClientCall<>();
        }

        @Override
        public String authority() {
            return authority;
        }

        @Override
        public ConnectivityState getState(boolean requestConnection) {
            return shutdown ? ConnectivityState.SHUTDOWN : ConnectivityState.READY;
        }

        @Override
        public void notifyWhenStateChanged(ConnectivityState source, Runnable callback) {
            if (!source.equals(getState(false))) {
                callback.run();
            }
        }
    }

    private static final class StatusManagedChannel extends ManagedChannel {
        private final String authority;
        private final Status closeStatus;
        private final AtomicInteger newCallCount = new AtomicInteger();
        private volatile boolean shutdown;

        private StatusManagedChannel(String authority, Status closeStatus) {
            this.authority = authority;
            this.closeStatus = closeStatus;
        }

        private int newCallCount() {
            return newCallCount.get();
        }

        @Override
        public ManagedChannel shutdown() {
            shutdown = true;
            return this;
        }

        @Override
        public ManagedChannel shutdownNow() {
            shutdown = true;
            return this;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
                MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions callOptions) {
            newCallCount.incrementAndGet();
            return new StatusClientCall<>(closeStatus);
        }

        @Override
        public String authority() {
            return authority;
        }
    }

    private static final class StatusClientCall<RequestT, ResponseT> extends ClientCall<RequestT, ResponseT> {
        private final Status closeStatus;

        private StatusClientCall(Status closeStatus) {
            this.closeStatus = closeStatus;
        }

        @Override
        public void start(Listener<ResponseT> responseListener, Metadata headers) {
            responseListener.onClose(closeStatus, new Metadata());
        }

        @Override
        public void request(int numMessages) {
        }

        @Override
        public void cancel(String message, Throwable cause) {
        }

        @Override
        public void halfClose() {
        }

        @Override
        public void sendMessage(RequestT message) {
        }
    }

    private static final class NoopClientCall<RequestT, ResponseT> extends ClientCall<RequestT, ResponseT> {
        @Override
        public void start(Listener<ResponseT> responseListener, Metadata headers) {
        }

        @Override
        public void request(int numMessages) {
        }

        @Override
        public void cancel(String message, Throwable cause) {
        }

        @Override
        public void halfClose() {
        }

        @Override
        public void sendMessage(RequestT message) {
        }
    }
}
