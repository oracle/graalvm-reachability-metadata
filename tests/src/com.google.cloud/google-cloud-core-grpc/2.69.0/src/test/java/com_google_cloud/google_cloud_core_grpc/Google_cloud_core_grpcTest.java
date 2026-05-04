/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud.google_cloud_core_grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.ChannelPoolSettings;
import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.api.gax.grpc.InstantiatingGrpcChannelProvider;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.api.gax.rpc.UnaryCallSettings;
import com.google.auth.ApiKeyCredentials;
import com.google.auth.Credentials;
import com.google.cloud.NoCredentials;
import com.google.cloud.Service;
import com.google.cloud.ServiceDefaults;
import com.google.cloud.ServiceFactory;
import com.google.cloud.ServiceOptions;
import com.google.cloud.ServiceRpc;
import com.google.cloud.TransportOptions;
import com.google.cloud.grpc.BaseGrpcServiceException;
import com.google.cloud.grpc.GrpcTransportOptions;
import com.google.cloud.spi.ServiceRpcFactory;
import io.grpc.Status;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class Google_cloud_core_grpcTest {
    @Test
    void defaultTransportOptionsExposeSharedExecutorFactory() throws Exception {
        GrpcTransportOptions options = GrpcTransportOptions.newBuilder().build();

        assertThat(options.getExecutorFactory()).isInstanceOf(GrpcTransportOptions.DefaultExecutorFactory.class);

        ScheduledExecutorService executor = options.getExecutorFactory().get();
        ScheduledExecutorService sameExecutor = options.getExecutorFactory().get();
        try {
            assertThat(sameExecutor).isSameAs(executor);
            assertThat(executor.submit(() -> "grpc-executor").get(5, TimeUnit.SECONDS)).isEqualTo("grpc-executor");
        } finally {
            options.getExecutorFactory().release(sameExecutor);
            options.getExecutorFactory().release(executor);
        }
    }

    @Test
    void builderToBuilderAndEqualityUseExecutorFactoryType() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        ScheduledExecutorService otherExecutor = Executors.newSingleThreadScheduledExecutor();
        try {
            CountingExecutorFactory factory = new CountingExecutorFactory(executor);
            GrpcTransportOptions options = GrpcTransportOptions.newBuilder().setExecutorFactory(factory).build();

            GrpcTransportOptions copied = options.toBuilder().build();
            GrpcTransportOptions sameFactoryType = GrpcTransportOptions.newBuilder()
                    .setExecutorFactory(new CountingExecutorFactory(otherExecutor))
                    .build();
            GrpcTransportOptions differentFactoryType = GrpcTransportOptions.newBuilder()
                    .setExecutorFactory(new AlternateExecutorFactory(otherExecutor))
                    .build();

            assertThat(options.getExecutorFactory()).isSameAs(factory);
            assertThat(copied.getExecutorFactory()).isSameAs(factory);
            assertThat(copied).isEqualTo(options).hasSameHashCodeAs(options);
            assertThat(sameFactoryType).isEqualTo(options).hasSameHashCodeAs(options);
            assertThat(differentFactoryType).isNotEqualTo(options);

            assertThat(factory.get()).isSameAs(executor);
            factory.release(executor);
            assertThat(factory.getCalls).isEqualTo(1);
            assertThat(factory.releaseCalls).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            otherExecutor.shutdownNow();
        }
    }

    @Test
    void apiCallSettingsUseProvidedRetrySettings() {
        RetrySettings retrySettings = RetrySettings.newBuilder()
                .setTotalTimeoutDuration(Duration.ofSeconds(10))
                .setInitialRetryDelayDuration(Duration.ofMillis(100))
                .setRetryDelayMultiplier(1.5)
                .setMaxRetryDelayDuration(Duration.ofSeconds(1))
                .setMaxAttempts(3)
                .setJittered(true)
                .setInitialRpcTimeoutDuration(Duration.ofSeconds(2))
                .setRpcTimeoutMultiplier(1.0)
                .setMaxRpcTimeoutDuration(Duration.ofSeconds(2))
                .build();

        UnaryCallSettings<?, ?> callSettings = GrpcTransportOptions.newBuilder()
                .build()
                .getApiCallSettings(retrySettings)
                .build();

        RetrySettings configuredRetrySettings = callSettings.getRetrySettings();

        assertThat(configuredRetrySettings.getTotalTimeoutDuration())
                .isEqualTo(retrySettings.getTotalTimeoutDuration());
        assertThat(configuredRetrySettings.getInitialRetryDelayDuration())
                .isEqualTo(retrySettings.getInitialRetryDelayDuration());
        assertThat(configuredRetrySettings.getRetryDelayMultiplier()).isEqualTo(retrySettings.getRetryDelayMultiplier());
        assertThat(configuredRetrySettings.getMaxRetryDelayDuration())
                .isEqualTo(retrySettings.getMaxRetryDelayDuration());
        assertThat(configuredRetrySettings.getMaxAttempts()).isEqualTo(retrySettings.getMaxAttempts());
        assertThat(configuredRetrySettings.isJittered()).isEqualTo(retrySettings.isJittered());
        assertThat(configuredRetrySettings.getInitialRpcTimeoutDuration())
                .isEqualTo(retrySettings.getInitialRpcTimeoutDuration());
        assertThat(configuredRetrySettings.getRpcTimeoutMultiplier()).isEqualTo(retrySettings.getRpcTimeoutMultiplier());
        assertThat(configuredRetrySettings.getMaxRpcTimeoutDuration())
                .isEqualTo(retrySettings.getMaxRpcTimeoutDuration());
        assertThat(callSettings.getRetryableCodes()).isEmpty();
    }

    @Test
    void setupHelpersCreateGrpcChannelAndCredentialProvidersFromServiceOptions() throws Exception {
        TestServiceOptions noCredentialsOptions = TestServiceOptions.newBuilder()
                .setProjectId("test-project")
                .setHost("localhost:8443")
                .setCredentials(NoCredentials.getInstance())
                .build();

        TransportChannelProvider channelProvider = GrpcTransportOptions.setUpChannelProvider(
                InstantiatingGrpcChannelProvider.newBuilder(), noCredentialsOptions);
        CredentialsProvider noCredentialsProvider = GrpcTransportOptions.setUpCredentialsProvider(noCredentialsOptions);

        assertThat(channelProvider).isInstanceOf(InstantiatingGrpcChannelProvider.class);
        assertThat(channelProvider.getEndpoint()).isEqualTo("localhost:8443");
        assertThat(channelProvider.getTransportName()).isEqualTo("grpc");
        assertThat(noCredentialsProvider).isInstanceOf(NoCredentialsProvider.class);
        assertThat(noCredentialsProvider.getCredentials()).isNull();

        Credentials apiKeyCredentials = ApiKeyCredentials.create("test-api-key");
        TestServiceOptions apiKeyOptions = TestServiceOptions.newBuilder()
                .setProjectId("test-project")
                .setHost("example.googleapis.com:443")
                .setCredentials(apiKeyCredentials)
                .build();

        CredentialsProvider fixedCredentialsProvider = GrpcTransportOptions.setUpCredentialsProvider(apiKeyOptions);

        assertThat(fixedCredentialsProvider).isInstanceOf(FixedCredentialsProvider.class);
        assertThat(fixedCredentialsProvider.getCredentials()).isSameAs(apiKeyCredentials);
    }

    @Test
    void setupChannelProviderPreservesGrpcChannelConfiguration() {
        Duration keepAliveTime = Duration.ofSeconds(30);
        Duration keepAliveTimeout = Duration.ofSeconds(5);
        ChannelPoolSettings channelPoolSettings = ChannelPoolSettings.staticallySized(2);
        TestServiceOptions serviceOptions = TestServiceOptions.newBuilder()
                .setProjectId("test-project")
                .setHost("configured.example.com:443")
                .setCredentials(NoCredentials.getInstance())
                .build();

        TransportChannelProvider channelProvider = GrpcTransportOptions.setUpChannelProvider(
                InstantiatingGrpcChannelProvider.newBuilder()
                        .setKeepAliveTimeDuration(keepAliveTime)
                        .setKeepAliveTimeoutDuration(keepAliveTimeout)
                        .setKeepAliveWithoutCalls(true)
                        .setMaxInboundMetadataSize(16 * 1024)
                        .setChannelPoolSettings(channelPoolSettings),
                serviceOptions);

        assertThat(channelProvider).isInstanceOf(InstantiatingGrpcChannelProvider.class);
        InstantiatingGrpcChannelProvider grpcChannelProvider = (InstantiatingGrpcChannelProvider) channelProvider;
        assertThat(grpcChannelProvider.getKeepAliveTimeDuration()).isEqualTo(keepAliveTime);
        assertThat(grpcChannelProvider.getKeepAliveTimeoutDuration()).isEqualTo(keepAliveTimeout);
        assertThat(grpcChannelProvider.getKeepAliveWithoutCalls()).isTrue();
        assertThat(grpcChannelProvider.getMaxInboundMetadataSize()).isEqualTo(16 * 1024);
        assertThat(grpcChannelProvider.getChannelPoolSettings()).isEqualTo(channelPoolSettings);
    }

    @Test
    void baseGrpcServiceExceptionCopiesApiExceptionDetails() {
        IllegalStateException cause = new IllegalStateException("transport failed");
        ApiException apiException = new ApiException(
                "backend unavailable", cause, GrpcStatusCode.of(Status.Code.UNAVAILABLE), true);

        BaseGrpcServiceException serviceException = new BaseGrpcServiceException(apiException);

        assertThat(serviceException)
                .hasMessage("backend unavailable")
                .hasCause(apiException);
        assertThat(serviceException.getCode()).isEqualTo(503);
        assertThat(serviceException.getReason()).isEqualTo("UNAVAILABLE");
        assertThat(serviceException.isRetryable()).isTrue();
        assertThat(serviceException.getLocation()).isNull();
        assertThat(serviceException.getDebugInfo()).isNull();
    }

    private static final class CountingExecutorFactory
            implements GrpcTransportOptions.ExecutorFactory<ScheduledExecutorService> {
        private final ScheduledExecutorService executor;
        private int getCalls;
        private int releaseCalls;

        private CountingExecutorFactory(ScheduledExecutorService executor) {
            this.executor = executor;
        }

        @Override
        public ScheduledExecutorService get() {
            getCalls++;
            return executor;
        }

        @Override
        public void release(ScheduledExecutorService executor) {
            assertThat(executor).isSameAs(this.executor);
            releaseCalls++;
        }
    }

    private static final class AlternateExecutorFactory
            implements GrpcTransportOptions.ExecutorFactory<ScheduledExecutorService> {
        private final ScheduledExecutorService executor;

        private AlternateExecutorFactory(ScheduledExecutorService executor) {
            this.executor = executor;
        }

        @Override
        public ScheduledExecutorService get() {
            return executor;
        }

        @Override
        public void release(ScheduledExecutorService executor) {
            assertThat(executor).isSameAs(this.executor);
        }
    }

    private static final class TestServiceOptions extends ServiceOptions<TestService, TestServiceOptions> {
        private TestServiceOptions(TestServiceOptionsBuilder builder) {
            super(TestServiceFactory.class, TestServiceRpcFactory.class, builder, new TestServiceDefaults());
        }

        private static TestServiceOptionsBuilder newBuilder() {
            return new TestServiceOptionsBuilder();
        }

        @Override
        protected boolean projectIdRequired() {
            return false;
        }

        @Override
        protected Set<String> getScopes() {
            return Collections.emptySet();
        }

        @Override
        public TestServiceOptionsBuilder toBuilder() {
            return new TestServiceOptionsBuilder(this);
        }
    }

    private static final class TestServiceOptionsBuilder
            extends ServiceOptions.Builder<TestService, TestServiceOptions, TestServiceOptionsBuilder> {
        private TestServiceOptionsBuilder() {
        }

        private TestServiceOptionsBuilder(TestServiceOptions options) {
            super(options);
        }

        @Override
        public TestServiceOptions build() {
            return new TestServiceOptions(this);
        }
    }

    private interface TestService extends Service<TestServiceOptions> {
    }

    private static final class TestServiceImpl implements TestService {
        private final TestServiceOptions options;

        private TestServiceImpl(TestServiceOptions options) {
            this.options = options;
        }

        @Override
        public TestServiceOptions getOptions() {
            return options;
        }
    }

    public static final class TestServiceFactory implements ServiceFactory<TestService, TestServiceOptions> {
        @Override
        public TestService create(TestServiceOptions options) {
            return new TestServiceImpl(options);
        }
    }

    private static final class TestServiceRpc implements ServiceRpc {
    }

    public static final class TestServiceRpcFactory implements ServiceRpcFactory<TestServiceOptions> {
        @Override
        public ServiceRpc create(TestServiceOptions options) {
            return new TestServiceRpc();
        }
    }

    private static final class TestServiceDefaults implements ServiceDefaults<TestService, TestServiceOptions> {
        @Override
        public ServiceFactory<TestService, TestServiceOptions> getDefaultServiceFactory() {
            return new TestServiceFactory();
        }

        @Override
        public ServiceRpcFactory<TestServiceOptions> getDefaultRpcFactory() {
            return new TestServiceRpcFactory();
        }

        @Override
        public TransportOptions getDefaultTransportOptions() {
            return GrpcTransportOptions.newBuilder().build();
        }
    }
}
