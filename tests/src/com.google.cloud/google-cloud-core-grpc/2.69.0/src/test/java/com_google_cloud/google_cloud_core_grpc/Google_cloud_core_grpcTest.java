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
import com.google.api.gax.grpc.InstantiatingGrpcChannelProvider;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.ApiExceptionFactory;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
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
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class Google_cloud_core_grpcTest {
    @Test
    void defaultTransportOptionsProvideSharedUsableExecutor()
            throws ExecutionException, InterruptedException, TimeoutException {
        GrpcTransportOptions transportOptions = GrpcTransportOptions.newBuilder().build();
        GrpcTransportOptions sameDefaults = GrpcTransportOptions.newBuilder().build();

        assertThat(transportOptions).isEqualTo(sameDefaults);
        assertThat(transportOptions.hashCode()).isEqualTo(sameDefaults.hashCode());

        GrpcTransportOptions.ExecutorFactory<ScheduledExecutorService> executorFactory =
                transportOptions.getExecutorFactory();
        ScheduledExecutorService firstExecutor = executorFactory.get();
        ScheduledExecutorService secondExecutor = executorFactory.get();
        try {
            assertThat(secondExecutor).isSameAs(firstExecutor);

            Future<String> future =
                    firstExecutor.schedule(() -> "scheduled", 1, TimeUnit.MILLISECONDS);

            assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("scheduled");
        } finally {
            executorFactory.release(secondExecutor);
            executorFactory.release(firstExecutor);
        }
    }

    @Test
    void customExecutorFactoryIsPreservedByBuilderAndReleased()
            throws ExecutionException, InterruptedException, TimeoutException {
        RecordingExecutorFactory executorFactory = new RecordingExecutorFactory();
        GrpcTransportOptions transportOptions =
                GrpcTransportOptions.newBuilder().setExecutorFactory(executorFactory).build();
        GrpcTransportOptions rebuiltOptions = transportOptions.toBuilder().build();
        GrpcTransportOptions equivalentOptions =
                GrpcTransportOptions.newBuilder()
                        .setExecutorFactory(new RecordingExecutorFactory())
                        .build();

        assertThat(transportOptions.getExecutorFactory()).isSameAs(executorFactory);
        assertThat(rebuiltOptions).isEqualTo(transportOptions);
        assertThat(rebuiltOptions.getExecutorFactory()).isSameAs(executorFactory);
        assertThat(transportOptions).isEqualTo(equivalentOptions);
        assertThat(transportOptions).isNotEqualTo(GrpcTransportOptions.newBuilder().build());

        ScheduledExecutorService executor = transportOptions.getExecutorFactory().get();
        try {
            Future<Integer> future = executor.submit(() -> 42);

            assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo(42);
            assertThat(executorFactory.getCalls()).isEqualTo(1);
        } finally {
            transportOptions.getExecutorFactory().release(executor);
        }

        assertThat(executorFactory.releaseCalls()).isEqualTo(1);
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void channelProviderUsesServiceOptionsHost() {
        String endpoint = "localhost:8443";
        TestServiceOptions serviceOptions = newTestOptions(endpoint, NoCredentials.getInstance());

        TransportChannelProvider channelProvider =
                GrpcTransportOptions.setUpChannelProvider(
                        InstantiatingGrpcChannelProvider.newBuilder(), serviceOptions);

        assertThat(channelProvider).isInstanceOf(InstantiatingGrpcChannelProvider.class);
        assertThat(channelProvider.getEndpoint()).isEqualTo(endpoint);
        assertThat(channelProvider.getTransportName()).isEqualTo("grpc");
        assertThat(channelProvider.needsEndpoint()).isFalse();
    }

    @Test
    void credentialsProviderUsesNoCredentialsProviderWhenCredentialsAreDisabled()
            throws IOException {
        TestServiceOptions serviceOptions =
                newTestOptions("example.googleapis.com:443", NoCredentials.getInstance());

        CredentialsProvider credentialsProvider =
                GrpcTransportOptions.setUpCredentialsProvider(serviceOptions);

        assertThat(credentialsProvider).isInstanceOf(NoCredentialsProvider.class);
        assertThat(credentialsProvider.getCredentials()).isNull();
    }

    @Test
    void credentialsProviderWrapsScopedServiceCredentials() throws IOException {
        TestCredentials credentials = new TestCredentials();
        TestServiceOptions serviceOptions =
                newTestOptions("example.googleapis.com:443", credentials);

        CredentialsProvider credentialsProvider =
                GrpcTransportOptions.setUpCredentialsProvider(serviceOptions);

        assertThat(credentialsProvider).isInstanceOf(FixedCredentialsProvider.class);
        assertThat(credentialsProvider.getCredentials()).isSameAs(credentials);
    }

    @Test
    void credentialsProviderScopesGoogleCredentialsBeforeWrapping() throws IOException {
        Set<String> scopes =
                Collections.singleton("https://www.googleapis.com/auth/cloud-platform");
        ScopingGoogleCredentials credentials = new ScopingGoogleCredentials();
        TestServiceOptions serviceOptions =
                newTestOptions("example.googleapis.com:443", credentials, scopes);

        CredentialsProvider credentialsProvider =
                GrpcTransportOptions.setUpCredentialsProvider(serviceOptions);

        assertThat(credentialsProvider).isInstanceOf(FixedCredentialsProvider.class);
        assertThat(credentialsProvider.getCredentials())
                .isInstanceOf(ScopingGoogleCredentials.class);
        ScopingGoogleCredentials scopedCredentials =
                (ScopingGoogleCredentials) credentialsProvider.getCredentials();
        assertThat(scopedCredentials).isNotSameAs(credentials);
        assertThat(scopedCredentials.scopes()).containsExactlyElementsOf(scopes);
        assertThat(credentials.createScopedCalls()).isEqualTo(1);
    }

    @Test
    void grpcServiceExceptionCopiesRetryableApiExceptionDetails() {
        Throwable cause = new IllegalStateException("backend unavailable");
        StatusCode statusCode = new FixedStatusCode(StatusCode.Code.UNAVAILABLE, "grpc-14");
        ApiException apiException =
                ApiExceptionFactory.createException("grpc request failed", cause, statusCode, true);

        BaseGrpcServiceException exception = new BaseGrpcServiceException(apiException);

        assertThat(exception).hasMessageContaining("grpc request failed");
        assertThat(exception.getCause()).isSameAs(apiException);
        assertThat(exception.getCode()).isEqualTo(StatusCode.Code.UNAVAILABLE.getHttpStatusCode());
        assertThat(exception.getReason()).isEqualTo("UNAVAILABLE");
        assertThat(exception.isRetryable()).isTrue();
        assertThat(exception.getLocation()).isNull();
        assertThat(exception.getDebugInfo()).isNull();
    }

    @Test
    void grpcServiceExceptionCopiesNonRetryableApiExceptionDetails() {
        StatusCode statusCode = new FixedStatusCode(StatusCode.Code.NOT_FOUND, "grpc-5");
        ApiException apiException =
                ApiExceptionFactory.createException("missing resource", null, statusCode, false);

        BaseGrpcServiceException exception = new BaseGrpcServiceException(apiException);

        assertThat(exception).hasMessageContaining("missing resource");
        assertThat(exception.getCause()).isSameAs(apiException);
        assertThat(exception.getCode()).isEqualTo(StatusCode.Code.NOT_FOUND.getHttpStatusCode());
        assertThat(exception.getReason()).isEqualTo("NOT_FOUND");
        assertThat(exception.isRetryable()).isFalse();
    }

    private static TestServiceOptions newTestOptions(String host, Credentials credentials) {
        return newTestOptions(host, credentials, Collections.emptySet());
    }

    private static TestServiceOptions newTestOptions(
            String host, Credentials credentials, Set<String> scopes) {
        return TestServiceOptions.newBuilder()
                .setProjectId("test-project")
                .setHost(host)
                .setCredentials(credentials)
                .setScopes(scopes)
                .build();
    }

    private static final class RecordingExecutorFactory
            implements GrpcTransportOptions.ExecutorFactory<ScheduledExecutorService> {
        private final ScheduledExecutorService executor =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            Thread thread =
                                    new Thread(runnable, "google-cloud-core-grpc-test-executor");
                            thread.setDaemon(true);
                            return thread;
                        });
        private final AtomicInteger getCalls = new AtomicInteger();
        private final AtomicInteger releaseCalls = new AtomicInteger();

        @Override
        public ScheduledExecutorService get() {
            getCalls.incrementAndGet();
            return executor;
        }

        @Override
        public void release(ScheduledExecutorService executor) {
            releaseCalls.incrementAndGet();
            executor.shutdownNow();
        }

        int getCalls() {
            return getCalls.get();
        }

        int releaseCalls() {
            return releaseCalls.get();
        }
    }

    private static final class FixedStatusCode implements StatusCode {
        private final StatusCode.Code code;
        private final Object transportCode;

        private FixedStatusCode(StatusCode.Code code, Object transportCode) {
            this.code = code;
            this.transportCode = transportCode;
        }

        @Override
        public StatusCode.Code getCode() {
            return code;
        }

        @Override
        public Object getTransportCode() {
            return transportCode;
        }
    }

    private static final class TestCredentials extends Credentials {
        @Override
        public String getAuthenticationType() {
            return "test";
        }

        @Override
        public Map<String, List<String>> getRequestMetadata(URI uri) {
            return Collections.singletonMap(
                    "Authorization", Collections.singletonList("Bearer test-token"));
        }

        @Override
        public boolean hasRequestMetadata() {
            return true;
        }

        @Override
        public boolean hasRequestMetadataOnly() {
            return true;
        }

        @Override
        public void refresh() {
        }
    }

    private static final class ScopingGoogleCredentials extends GoogleCredentials {
        private static final long serialVersionUID = 1L;
        private final Set<String> scopes;
        private int createScopedCalls;

        private ScopingGoogleCredentials() {
            this(Collections.emptySet());
        }

        private ScopingGoogleCredentials(Collection<String> scopes) {
            super();
            this.scopes = Collections.unmodifiableSet(new LinkedHashSet<>(scopes));
        }

        @Override
        public boolean createScopedRequired() {
            return scopes.isEmpty();
        }

        @Override
        public GoogleCredentials createScoped(Collection<String> scopes) {
            createScopedCalls++;
            return new ScopingGoogleCredentials(scopes);
        }

        Set<String> scopes() {
            return scopes;
        }

        int createScopedCalls() {
            return createScopedCalls;
        }
    }

    private interface TestService extends Service<TestServiceOptions> {
    }

    public static final class TestServiceImpl implements TestService {
        private final TestServiceOptions options;

        private TestServiceImpl(TestServiceOptions options) {
            this.options = options;
        }

        @Override
        public TestServiceOptions getOptions() {
            return options;
        }
    }

    public static final class TestServiceRpc implements ServiceRpc {
    }

    public static final class TestServiceFactory
            implements ServiceFactory<TestService, TestServiceOptions> {
        @Override
        public TestService create(TestServiceOptions options) {
            return new TestServiceImpl(options);
        }
    }

    public static final class TestServiceRpcFactory
            implements ServiceRpcFactory<TestServiceOptions> {
        @Override
        public ServiceRpc create(TestServiceOptions options) {
            return new TestServiceRpc();
        }
    }

    private static final class TestServiceDefaults
            implements ServiceDefaults<TestService, TestServiceOptions> {
        private static final TestServiceDefaults INSTANCE = new TestServiceDefaults();

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

    public static final class TestServiceOptions
            extends ServiceOptions<TestService, TestServiceOptions> {
        private static final long serialVersionUID = 1L;
        private final Set<String> scopes;

        private TestServiceOptions(Builder builder) {
            super(
                    TestServiceFactory.class,
                    TestServiceRpcFactory.class,
                    builder,
                    TestServiceDefaults.INSTANCE);
            this.scopes = builder.scopes;
        }

        static Builder newBuilder() {
            return new Builder();
        }

        @Override
        protected boolean projectIdRequired() {
            return false;
        }

        @Override
        protected Set<String> getScopes() {
            return scopes;
        }

        @Override
        public Builder toBuilder() {
            return new Builder(this);
        }

        public static final class Builder
                extends ServiceOptions.Builder<TestService, TestServiceOptions, Builder> {
            private Set<String> scopes = Collections.emptySet();

            private Builder() {
            }

            private Builder(TestServiceOptions options) {
                super(options);
                this.scopes = options.scopes;
            }

            private Builder setScopes(Set<String> scopes) {
                this.scopes = Collections.unmodifiableSet(new LinkedHashSet<>(scopes));
                return this;
            }

            @Override
            public TestServiceOptions build() {
                return new TestServiceOptions(this);
            }
        }
    }
}
