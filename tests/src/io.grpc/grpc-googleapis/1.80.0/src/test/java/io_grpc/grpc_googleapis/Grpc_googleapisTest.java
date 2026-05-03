/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_googleapis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.ChannelLogger;
import io.grpc.EquivalentAddressGroup;
import io.grpc.MetricRecorder;
import io.grpc.NameResolver;
import io.grpc.NameResolverRegistry;
import io.grpc.Status;
import io.grpc.StatusOr;
import io.grpc.SynchronizationContext;
import io.grpc.googleapis.GoogleCloudToProdExperimentalNameResolverProvider;
import io.grpc.googleapis.GoogleCloudToProdNameResolverProvider;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class Grpc_googleapisTest {
    @Test
    void googleCloudToProdProviderCreatesDnsBackedResolverForMatchingScheme() throws InterruptedException {
        try (ResolverArgsResources resources = new ResolverArgsResources()) {
            GoogleCloudToProdNameResolverProvider provider = new GoogleCloudToProdNameResolverProvider();

            NameResolver resolver = provider.newNameResolver(URI.create("google-c2p:///localhost"), resources.args);

            assertThat(provider.getDefaultScheme()).isEqualTo("google-c2p");
            assertThat(provider.getProducedSocketAddressTypes()).containsExactly(InetSocketAddress.class);
            assertThat(resolver).isNotNull();
            assertThat(resolver.getServiceAuthority()).isEqualTo("localhost");
            assertThat(provider.newNameResolver(URI.create("dns:///localhost"), resources.args)).isNull();

            resolver.shutdown();
        }
    }

    @Test
    void experimentalProviderUsesExperimentalScheme() throws InterruptedException {
        try (ResolverArgsResources resources = new ResolverArgsResources()) {
            GoogleCloudToProdExperimentalNameResolverProvider provider =
                            new GoogleCloudToProdExperimentalNameResolverProvider();

            NameResolver resolver = provider.newNameResolver(
                            URI.create("google-c2p-experimental:///localhost"), resources.args);

            assertThat(provider.getDefaultScheme()).isEqualTo("google-c2p-experimental");
            assertThat(resolver).isNotNull();
            assertThat(resolver.getServiceAuthority()).isEqualTo("localhost");
            assertThat(provider.newNameResolver(URI.create("google-c2p:///localhost"), resources.args)).isNull();

            resolver.shutdown();
        }
    }

    @Test
    void experimentalProviderAdvertisesDnsCompatibleSocketAddresses() {
        GoogleCloudToProdExperimentalNameResolverProvider provider =
                        new GoogleCloudToProdExperimentalNameResolverProvider();

        assertThat(provider.getProducedSocketAddressTypes()).containsExactly(InetSocketAddress.class);
    }

    @Test
    void resolverDerivesServiceAuthorityFromTargetPathWithUriAuthority() throws InterruptedException {
        try (ResolverArgsResources resources = new ResolverArgsResources()) {
            GoogleCloudToProdNameResolverProvider provider = new GoogleCloudToProdNameResolverProvider();

            NameResolver resolver = provider.newNameResolver(
                            URI.create("google-c2p://resolver-authority/localhost:8443"), resources.args);

            assertThat(resolver).isNotNull();
            assertThat(resolver.getServiceAuthority()).isEqualTo("localhost:8443");

            resolver.shutdown();
        }
    }

    @Test
    void resolverRejectsTargetsWithoutAPathComponent() throws InterruptedException {
        try (ResolverArgsResources resources = new ResolverArgsResources()) {
            GoogleCloudToProdNameResolverProvider provider = new GoogleCloudToProdNameResolverProvider();

            assertThatThrownBy(() -> provider.newNameResolver(
                            URI.create("google-c2p://resolver-authority"), resources.args))
                            .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void providersAreDiscoverableThroughDefaultNameResolverRegistry() throws InterruptedException {
        try (ResolverArgsResources resources = new ResolverArgsResources()) {
            NameResolverRegistry registry = NameResolverRegistry.getDefaultRegistry();

            assertThat(registry.getProviderForScheme("google-c2p"))
                            .isInstanceOf(GoogleCloudToProdNameResolverProvider.class);
            assertThat(registry.getProviderForScheme("google-c2p-experimental"))
                            .isInstanceOf(GoogleCloudToProdExperimentalNameResolverProvider.class);

            NameResolver resolver = registry.asFactory()
                            .newNameResolver(URI.create("google-c2p:///localhost"), resources.args);

            assertThat(resolver).isNotNull();
            assertThat(resolver.getServiceAuthority()).isEqualTo("localhost");

            resolver.shutdown();
        }
    }

    @Test
    void resolverDelegatesResolutionToUnderlyingDnsResolver() throws InterruptedException {
        try (ResolverArgsResources resources = new ResolverArgsResources()) {
            NameResolver resolver = new GoogleCloudToProdNameResolverProvider()
                            .newNameResolver(URI.create("google-c2p:///localhost"), resources.args);
            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<NameResolver.ResolutionResult> result = new AtomicReference<>();
            AtomicReference<Status> error = new AtomicReference<>();

            try {
                resolver.start(new NameResolver.Listener2() {
                    @Override
                    public void onResult(NameResolver.ResolutionResult resolutionResult) {
                        result.set(resolutionResult);
                        completed.countDown();
                    }

                    @Override
                    public void onError(Status status) {
                        error.set(status);
                        completed.countDown();
                    }
                });

                assertThat(completed.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(error.get()).isNull();

                StatusOr<List<EquivalentAddressGroup>> addressesOrError = result.get().getAddressesOrError();
                assertThat(addressesOrError.hasValue()).isTrue();
                assertThat(addressesOrError.getValue()).isNotEmpty();
                assertThat(addressesOrError.getValue())
                                .flatExtracting(EquivalentAddressGroup::getAddresses)
                                .anySatisfy(Grpc_googleapisTest::assertInetSocketAddress);

                resolver.refresh();
            } finally {
                resolver.shutdown();
            }
        }
    }

    private static NameResolver.Args newNameResolverArgs(ScheduledExecutorService scheduler) {
        SynchronizationContext synchronizationContext = new SynchronizationContext((thread, throwable) -> {
            throw new AssertionError("Unexpected resolver exception on " + thread.getName(), throwable);
        });
        return NameResolver.Args.newBuilder()
                        .setDefaultPort(443)
                        .setProxyDetector(serverAddress -> null)
                        .setSynchronizationContext(synchronizationContext)
                        .setScheduledExecutorService(scheduler)
                        .setServiceConfigParser(new NameResolver.ServiceConfigParser() {
                            @Override
                            public NameResolver.ConfigOrError parseServiceConfig(Map<String, ?> rawServiceConfig) {
                                return NameResolver.ConfigOrError.fromConfig(rawServiceConfig);
                            }
                        })
                        .setChannelLogger(new NoOpChannelLogger())
                        .setOffloadExecutor(Runnable::run)
                        .setMetricRecorder(new MetricRecorder() {
                        })
                        .build();
    }

    private static ScheduledExecutorService newDaemonScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "grpc-googleapis-test-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static void assertInetSocketAddress(SocketAddress address) {
        assertThat(address).isInstanceOf(InetSocketAddress.class);
    }

    private static final class ResolverArgsResources implements AutoCloseable {
        private final ScheduledExecutorService scheduler = newDaemonScheduler();
        private final NameResolver.Args args = newNameResolverArgs(scheduler);

        @Override
        public void close() throws InterruptedException {
            scheduler.shutdownNow();
            assertThat(scheduler.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class NoOpChannelLogger extends ChannelLogger {
        @Override
        public void log(ChannelLogger.ChannelLogLevel level, String message) {
        }

        @Override
        public void log(ChannelLogger.ChannelLogLevel level, String messageFormat, Object... args) {
        }
    }
}
