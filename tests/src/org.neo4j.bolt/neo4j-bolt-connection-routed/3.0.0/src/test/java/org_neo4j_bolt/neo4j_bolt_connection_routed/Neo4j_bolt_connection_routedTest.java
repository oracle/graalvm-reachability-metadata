/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_neo4j_bolt.neo4j_bolt_connection_routed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.neo4j.bolt.connection.AccessMode;
import org.neo4j.bolt.connection.AuthInfo;
import org.neo4j.bolt.connection.AuthToken;
import org.neo4j.bolt.connection.BoltAgent;
import org.neo4j.bolt.connection.BoltConnection;
import org.neo4j.bolt.connection.BoltConnectionProvider;
import org.neo4j.bolt.connection.BoltConnectionState;
import org.neo4j.bolt.connection.BoltProtocolVersion;
import org.neo4j.bolt.connection.BoltServerAddress;
import org.neo4j.bolt.connection.ClusterComposition;
import org.neo4j.bolt.connection.DatabaseName;
import org.neo4j.bolt.connection.DatabaseNameUtil;
import org.neo4j.bolt.connection.DomainNameResolver;
import org.neo4j.bolt.connection.LoggingProvider;
import org.neo4j.bolt.connection.NotificationConfig;
import org.neo4j.bolt.connection.ResponseHandler;
import org.neo4j.bolt.connection.RoutingContext;
import org.neo4j.bolt.connection.SecurityPlan;
import org.neo4j.bolt.connection.SecurityPlans;
import org.neo4j.bolt.connection.exception.BoltServiceUnavailableException;
import org.neo4j.bolt.connection.message.Message;
import org.neo4j.bolt.connection.routed.ClusterCompositionLookupResult;
import org.neo4j.bolt.connection.routed.Rediscovery;
import org.neo4j.bolt.connection.routed.RoutedBoltConnectionProvider;
import org.neo4j.bolt.connection.routed.RoutingTable;

public class Neo4j_bolt_connection_routedTest {
    private static final SecurityPlan SECURITY_PLAN = SecurityPlans.unencrypted();
    private static final BoltAgent BOLT_AGENT = new BoltAgent("test-client", "test-platform", "Java", "JDK");
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void clusterCompositionLookupResultPreservesCompositionAndOptionalRouters() {
        BoltServerAddress router = new BoltServerAddress("router.example", 7687);
        ClusterComposition composition = new ClusterComposition(
                Clock.systemUTC().millis() + 60_000,
                linkedSet(new BoltServerAddress("reader.example", 7687)),
                linkedSet(new BoltServerAddress("writer.example", 7687)),
                linkedSet(router),
                "neo4j");

        ClusterCompositionLookupResult withoutResolvedRouters = new ClusterCompositionLookupResult(composition);
        ClusterCompositionLookupResult withResolvedRouters =
                new ClusterCompositionLookupResult(composition, linkedSet(router));

        assertThat(withoutResolvedRouters.getClusterComposition()).isSameAs(composition);
        assertThat(withoutResolvedRouters.getResolvedInitialRouters()).isEmpty();
        assertThat(withResolvedRouters.getClusterComposition()).isSameAs(composition);
        assertThat(withResolvedRouters.getResolvedInitialRouters()).hasValue(linkedSet(router));
    }

    @Test
    void connectDiscoversHomeDatabaseAndBalancesLeastConnectedWriters() throws Exception {
        BoltServerAddress firstWriter = new BoltServerAddress("writer-a.example", 7687);
        BoltServerAddress secondWriter = new BoltServerAddress("writer-b.example", 7687);
        RecordingProvider firstProvider = new RecordingProvider(firstWriter);
        RecordingProvider secondProvider = new RecordingProvider(secondWriter);
        StubRediscovery rediscovery = new StubRediscovery(new ClusterCompositionLookupResult(new ClusterComposition(
                Clock.systemUTC().millis() + 60_000,
                linkedSet(new BoltServerAddress("reader.example", 7687)),
                linkedSet(firstWriter, secondWriter),
                linkedSet(new BoltServerAddress("router.example", 7687)),
                "neo4j")));
        RoutedBoltConnectionProvider provider = routedProvider(address -> {
            if (address.equals(firstWriter)) {
                return firstProvider;
            }
            if (address.equals(secondWriter)) {
                return secondProvider;
            }
            throw new IllegalArgumentException("Unexpected address: " + address);
        }, rediscovery);
        AtomicReference<DatabaseName> resolvedDatabase = new AtomicReference<>();

        BoltConnection firstConnection = await(connect(provider, null, AccessMode.WRITE, resolvedDatabase::set));
        BoltConnection secondConnection = await(connect(
                provider,
                DatabaseNameUtil.database("neo4j"),
                AccessMode.WRITE,
                Neo4j_bolt_connection_routedTest::ignore));

        assertThat(firstConnection.serverAddress()).isEqualTo(firstWriter);
        assertThat(secondConnection.serverAddress()).isEqualTo(secondWriter);
        assertThat(resolvedDatabase.get().databaseName()).hasValue("neo4j");
        assertThat(rediscovery.lookupCount()).isEqualTo(1);
        assertThat(firstProvider.connectAttempts()).isEqualTo(1);
        assertThat(secondProvider.connectAttempts()).isEqualTo(1);
        await(firstConnection.write(Collections.emptyList()));
        assertThat(firstProvider.connections())
                .singleElement()
                .satisfies(connection -> assertThat(connection.writeAttempts()).isEqualTo(1));

        await(firstConnection.close());
        BoltConnection thirdConnection = await(connect(
                provider,
                DatabaseNameUtil.database("neo4j"),
                AccessMode.WRITE,
                Neo4j_bolt_connection_routedTest::ignore));

        assertThat(thirdConnection.serverAddress()).isEqualTo(firstWriter);
        assertThat(firstProvider.connectAttempts()).isEqualTo(2);
        assertThat(secondProvider.connectAttempts()).isEqualTo(1);

        await(secondConnection.close());
        await(thirdConnection.close());
        await(provider.close());
        assertThat(firstProvider.closeAttempts()).isEqualTo(1);
        assertThat(secondProvider.closeAttempts()).isEqualTo(1);
    }

    @Test
    void connectRetriesNextWriterAfterServiceUnavailableAndForgetsFailedAddress() throws Exception {
        BoltServerAddress failingWriter = new BoltServerAddress("writer-down.example", 7687);
        BoltServerAddress workingWriter = new BoltServerAddress("writer-up.example", 7687);
        RecordingProvider failingProvider = new RecordingProvider(failingWriter);
        failingProvider.enqueueFailure(new BoltServiceUnavailableException("writer is down"));
        RecordingProvider workingProvider = new RecordingProvider(workingWriter);
        StubRediscovery rediscovery = new StubRediscovery(new ClusterCompositionLookupResult(new ClusterComposition(
                Clock.systemUTC().millis() + 60_000,
                linkedSet(new BoltServerAddress("reader.example", 7687)),
                linkedSet(failingWriter, workingWriter),
                linkedSet(new BoltServerAddress("router.example", 7687)),
                "neo4j")));
        RoutedBoltConnectionProvider provider = routedProvider(address -> {
            if (address.equals(failingWriter)) {
                return failingProvider;
            }
            if (address.equals(workingWriter)) {
                return workingProvider;
            }
            throw new IllegalArgumentException("Unexpected address: " + address);
        }, rediscovery);

        BoltConnection recoveredConnection = await(connect(
                provider,
                DatabaseNameUtil.database("neo4j"),
                AccessMode.WRITE,
                Neo4j_bolt_connection_routedTest::ignore));

        assertThat(recoveredConnection.serverAddress()).isEqualTo(workingWriter);
        assertThat(failingProvider.connectAttempts()).isEqualTo(1);
        assertThat(workingProvider.connectAttempts()).isEqualTo(1);

        await(recoveredConnection.close());
        BoltConnection nextConnection = await(connect(
                provider,
                DatabaseNameUtil.database("neo4j"),
                AccessMode.WRITE,
                Neo4j_bolt_connection_routedTest::ignore));

        assertThat(nextConnection.serverAddress()).isEqualTo(workingWriter);
        assertThat(failingProvider.connectAttempts()).isEqualTo(1);
        assertThat(workingProvider.connectAttempts()).isEqualTo(2);

        await(nextConnection.close());
        await(provider.close());
    }

    @Test
    void writeAndFlushServiceUnavailableForgetsServerAndReportsRoutedError() throws Exception {
        BoltServerAddress failingWriter = new BoltServerAddress("writer-runtime-down.example", 7687);
        BoltServerAddress workingWriter = new BoltServerAddress("writer-runtime-up.example", 7687);
        RecordingProvider failingProvider = new RecordingProvider(failingWriter);
        RecordingProvider workingProvider = new RecordingProvider(workingWriter);
        StubRediscovery rediscovery = new StubRediscovery(new ClusterCompositionLookupResult(new ClusterComposition(
                Clock.systemUTC().millis() + 60_000,
                linkedSet(new BoltServerAddress("reader.example", 7687)),
                linkedSet(failingWriter, workingWriter),
                linkedSet(new BoltServerAddress("router.example", 7687)),
                "neo4j")));
        RoutedBoltConnectionProvider provider = routedProvider(address -> {
            if (address.equals(failingWriter)) {
                return failingProvider;
            }
            if (address.equals(workingWriter)) {
                return workingProvider;
            }
            throw new IllegalArgumentException("Unexpected address: " + address);
        }, rediscovery);
        BoltConnection failedConnection = await(connect(
                provider,
                DatabaseNameUtil.database("neo4j"),
                AccessMode.WRITE,
                Neo4j_bolt_connection_routedTest::ignore));
        failingProvider.connections()
                .get(0)
                .enqueueFlushFailure(new BoltServiceUnavailableException("connection lost while flushing"));
        AtomicReference<Throwable> reportedError = new AtomicReference<>();

        await(failedConnection.writeAndFlush(reportedError::set, Collections.emptyList()));

        assertThat(reportedError.get())
                .isInstanceOf(BoltServiceUnavailableException.class)
                .hasMessageContaining(failingWriter.toString())
                .hasCauseInstanceOf(BoltServiceUnavailableException.class);
        assertThat(rediscovery.lookupCount()).isEqualTo(1);

        await(failedConnection.close());
        BoltConnection recoveredConnection = await(connect(
                provider,
                DatabaseNameUtil.database("neo4j"),
                AccessMode.WRITE,
                Neo4j_bolt_connection_routedTest::ignore));

        assertThat(recoveredConnection.serverAddress()).isEqualTo(workingWriter);
        assertThat(failingProvider.connectAttempts()).isEqualTo(1);
        assertThat(workingProvider.connectAttempts()).isEqualTo(1);
        assertThat(rediscovery.lookupCount()).isEqualTo(1);

        await(recoveredConnection.close());
        await(provider.close());
    }

    @Test
    void supportsFeatureDetectionUsesResolvedRoutersAndClosesProbeConnections() throws Exception {
        BoltServerAddress unavailableRouter = new BoltServerAddress("router-down.example", 7687);
        BoltServerAddress modernRouter = new BoltServerAddress("router-modern.example", 7687);
        RecordingProvider unavailableProvider = new RecordingProvider(unavailableRouter);
        unavailableProvider.enqueueFailure(new BoltServiceUnavailableException("router is down"));
        unavailableProvider.enqueueFailure(new BoltServiceUnavailableException("router is still down"));
        RecordingProvider modernProvider = new RecordingProvider(modernRouter, new BoltProtocolVersion(5, 1));
        StubRediscovery rediscovery = new StubRediscovery(
                new ClusterCompositionLookupResult(new ClusterComposition(
                        Clock.systemUTC().millis() + 60_000,
                        linkedSet(modernRouter),
                        linkedSet(modernRouter),
                        linkedSet(modernRouter),
                        "neo4j")),
                List.of(unavailableRouter, modernRouter));
        RoutedBoltConnectionProvider provider = routedProvider(address -> {
            if (address.equals(unavailableRouter)) {
                return unavailableProvider;
            }
            if (address.equals(modernRouter)) {
                return modernProvider;
            }
            throw new IllegalArgumentException("Unexpected address: " + address);
        }, rediscovery);

        Boolean supportsMultiDb = await(provider.supportsMultiDb(
                null, null, null, null, 0, SECURITY_PLAN, null));
        Boolean supportsSessionAuth = await(provider.supportsSessionAuth(
                null, null, null, null, 0, SECURITY_PLAN, null));

        assertThat(supportsMultiDb).isTrue();
        assertThat(supportsSessionAuth).isTrue();
        assertThat(rediscovery.resolveCount()).isEqualTo(2);
        assertThat(unavailableProvider.connectAttempts()).isEqualTo(2);
        assertThat(modernProvider.connectAttempts()).isEqualTo(2);
        assertThat(modernProvider.connections())
                .allSatisfy(connection -> assertThat(connection.closeAttempts()).isEqualTo(1));

        await(provider.close());
    }

    @Test
    void verifyConnectivityUsesSystemDatabaseWhenMultiDbIsSupported() throws Exception {
        BoltServerAddress router = new BoltServerAddress("router.example", 7687);
        RecordingProvider routerProvider = new RecordingProvider(router, new BoltProtocolVersion(4, 0));
        StubRediscovery rediscovery = new StubRediscovery(new ClusterCompositionLookupResult(new ClusterComposition(
                Clock.systemUTC().millis() + 60_000,
                linkedSet(router),
                linkedSet(router),
                linkedSet(router),
                "system")), List.of(router));
        RoutedBoltConnectionProvider provider = routedProvider(address -> routerProvider, rediscovery);

        await(provider.verifyConnectivity(null, null, null, null, 0, SECURITY_PLAN, null));

        assertThat(rediscovery.resolveCount()).isEqualTo(1);
        assertThat(rediscovery.lookupCount()).isEqualTo(1);
        assertThat(rediscovery.lookupDatabases())
                .singleElement()
                .satisfies(database -> assertThat(database.databaseName()).hasValue("system"));
        assertThat(routerProvider.connectAttempts()).isEqualTo(1);
        assertThat(routerProvider.connections())
                .singleElement()
                .satisfies(connection -> assertThat(connection.closeAttempts()).isEqualTo(1));

        await(provider.close());
    }

    @Test
    void verifyConnectivityUsesDefaultDatabaseWhenMultiDbIsNotSupported() throws Exception {
        BoltServerAddress router = new BoltServerAddress("legacy-router.example", 7687);
        RecordingProvider routerProvider = new RecordingProvider(router, new BoltProtocolVersion(3, 5));
        StubRediscovery rediscovery = new StubRediscovery(new ClusterCompositionLookupResult(new ClusterComposition(
                Clock.systemUTC().millis() + 60_000,
                linkedSet(router),
                linkedSet(router),
                linkedSet(router),
                null)), List.of(router));
        RoutedBoltConnectionProvider provider = routedProvider(address -> routerProvider, rediscovery);

        await(provider.verifyConnectivity(null, null, null, null, 0, SECURITY_PLAN, null));

        assertThat(rediscovery.resolveCount()).isEqualTo(1);
        assertThat(rediscovery.lookupCount()).isEqualTo(1);
        assertThat(rediscovery.lookupDatabases())
                .singleElement()
                .satisfies(database -> assertThat(database.databaseName()).isEmpty());
        assertThat(routerProvider.connectAttempts()).isEqualTo(1);
        assertThat(routerProvider.connections())
                .singleElement()
                .satisfies(connection -> assertThat(connection.closeAttempts()).isEqualTo(1));

        await(provider.close());
    }

    @Test
    void closedProviderRejectsNewConnections() throws Exception {
        BoltServerAddress writer = new BoltServerAddress("writer.example", 7687);
        RecordingProvider writerProvider = new RecordingProvider(writer);
        StubRediscovery rediscovery = new StubRediscovery(new ClusterCompositionLookupResult(new ClusterComposition(
                Clock.systemUTC().millis() + 60_000,
                linkedSet(writer),
                linkedSet(writer),
                linkedSet(writer),
                "neo4j")));
        RoutedBoltConnectionProvider provider = routedProvider(address -> writerProvider, rediscovery);

        await(provider.close());

        assertThatThrownBy(() -> await(connect(
                        provider,
                        DatabaseNameUtil.database("neo4j"),
                        AccessMode.WRITE,
                        Neo4j_bolt_connection_routedTest::ignore)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Connection provider is closed.");
    }

    private static CompletionStage<BoltConnection> connect(
            RoutedBoltConnectionProvider provider,
            DatabaseName databaseName,
            AccessMode mode,
            Consumer<DatabaseName> databaseNameConsumer) {
        return provider.connect(
                null,
                RoutingContext.EMPTY,
                BOLT_AGENT,
                "test-user-agent",
                1_000,
                SECURITY_PLAN,
                databaseName,
                () -> CompletableFuture.completedFuture(null),
                mode,
                Collections.emptySet(),
                null,
                null,
                null,
                databaseNameConsumer,
                Collections.emptyMap());
    }

    private static RoutedBoltConnectionProvider routedProvider(
            Function<BoltServerAddress, BoltConnectionProvider> providerFactory, Rediscovery rediscovery) {
        DomainNameResolver domainNameResolver = host -> new InetAddress[0];
        return new RoutedBoltConnectionProvider(
                providerFactory,
                address -> Set.of(address),
                domainNameResolver,
                60_000,
                rediscovery,
                Clock.systemUTC(),
                new NoopLoggingProvider(),
                BoltServerAddress.LOCAL_DEFAULT,
                RoutingContext.EMPTY,
                BOLT_AGENT,
                "test-user-agent",
                1_000,
                null);
    }

    @SafeVarargs
    private static <T> LinkedHashSet<T> linkedSet(T... values) {
        LinkedHashSet<T> set = new LinkedHashSet<>();
        Collections.addAll(set, values);
        return set;
    }

    private static void ignore(DatabaseName ignored) {
        // ignored
    }

    private static <T> T await(CompletionStage<T> stage) throws Exception {
        try {
            return stage.toCompletableFuture().get(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof CompletionException completionException && completionException.getCause() != null) {
                cause = completionException.getCause();
            }
            if (cause instanceof Exception checkedException) {
                throw checkedException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new AssertionError(cause);
        }
    }

    private static final class StubRediscovery implements Rediscovery {
        private final ClusterCompositionLookupResult lookupResult;
        private final List<BoltServerAddress> resolvedAddresses;
        private final List<DatabaseName> lookupDatabases = new ArrayList<>();
        private final AtomicInteger lookupCount = new AtomicInteger();
        private final AtomicInteger resolveCount = new AtomicInteger();

        private StubRediscovery(ClusterCompositionLookupResult lookupResult) {
            this(lookupResult, List.copyOf(lookupResult.getClusterComposition().routers()));
        }

        private StubRediscovery(
                ClusterCompositionLookupResult lookupResult, List<BoltServerAddress> resolvedAddresses) {
            this.lookupResult = lookupResult;
            this.resolvedAddresses = resolvedAddresses;
        }

        @Override
        public CompletionStage<ClusterCompositionLookupResult> lookupClusterComposition(
                SecurityPlan securityPlan,
                RoutingTable routingTable,
                Function<BoltServerAddress, BoltConnectionProvider> connectionProviderGetter,
                Set<String> bookmarks,
                String impersonatedUser,
                Supplier<CompletionStage<AuthToken>> authTokenStageSupplier,
                BoltProtocolVersion minVersion) {
            lookupCount.incrementAndGet();
            lookupDatabases.add(routingTable.database());
            return CompletableFuture.completedFuture(lookupResult);
        }

        @Override
        public List<BoltServerAddress> resolve() {
            resolveCount.incrementAndGet();
            return resolvedAddresses;
        }

        private int lookupCount() {
            return lookupCount.get();
        }

        private List<DatabaseName> lookupDatabases() {
            return lookupDatabases;
        }

        private int resolveCount() {
            return resolveCount.get();
        }
    }

    private static final class RecordingProvider implements BoltConnectionProvider {
        private final BoltServerAddress address;
        private final BoltProtocolVersion protocolVersion;
        private final ArrayDeque<Throwable> failures = new ArrayDeque<>();
        private final List<RecordingConnection> connections = new ArrayList<>();
        private final AtomicInteger connectAttempts = new AtomicInteger();
        private final AtomicInteger closeAttempts = new AtomicInteger();

        private RecordingProvider(BoltServerAddress address) {
            this(address, new BoltProtocolVersion(5, 0));
        }

        private RecordingProvider(BoltServerAddress address, BoltProtocolVersion protocolVersion) {
            this.address = address;
            this.protocolVersion = protocolVersion;
        }

        @Override
        public CompletionStage<BoltConnection> connect(
                BoltServerAddress address,
                RoutingContext routingContext,
                BoltAgent boltAgent,
                String userAgent,
                int connectTimeoutMillis,
                SecurityPlan securityPlan,
                DatabaseName databaseName,
                Supplier<CompletionStage<AuthToken>> authTokenStageSupplier,
                AccessMode mode,
                Set<String> bookmarks,
                String impersonatedUser,
                BoltProtocolVersion minVersion,
                NotificationConfig notificationConfig,
                Consumer<DatabaseName> databaseNameConsumer,
                Map<String, Object> additionalParameters) {
            connectAttempts.incrementAndGet();
            Throwable failure = failures.pollFirst();
            if (failure != null) {
                return CompletableFuture.failedFuture(failure);
            }
            RecordingConnection connection = new RecordingConnection(this.address, protocolVersion);
            connections.add(connection);
            return CompletableFuture.completedFuture(connection);
        }

        @Override
        public CompletionStage<Void> verifyConnectivity(
                BoltServerAddress address,
                RoutingContext routingContext,
                BoltAgent boltAgent,
                String userAgent,
                int connectTimeoutMillis,
                SecurityPlan securityPlan,
                AuthToken authToken) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Boolean> supportsMultiDb(
                BoltServerAddress address,
                RoutingContext routingContext,
                BoltAgent boltAgent,
                String userAgent,
                int connectTimeoutMillis,
                SecurityPlan securityPlan,
                AuthToken authToken) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletionStage<Boolean> supportsSessionAuth(
                BoltServerAddress address,
                RoutingContext routingContext,
                BoltAgent boltAgent,
                String userAgent,
                int connectTimeoutMillis,
                SecurityPlan securityPlan,
                AuthToken authToken) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletionStage<Void> close() {
            closeAttempts.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }

        private void enqueueFailure(Throwable failure) {
            failures.addLast(failure);
        }

        private int connectAttempts() {
            return connectAttempts.get();
        }

        private int closeAttempts() {
            return closeAttempts.get();
        }

        private List<RecordingConnection> connections() {
            return connections;
        }
    }

    private static final class RecordingConnection implements BoltConnection {
        private final BoltServerAddress address;
        private final BoltProtocolVersion protocolVersion;
        private final AtomicInteger closeAttempts = new AtomicInteger();
        private final AtomicInteger writeAttempts = new AtomicInteger();
        private final ArrayDeque<Throwable> flushFailures = new ArrayDeque<>();

        private RecordingConnection(BoltServerAddress address, BoltProtocolVersion protocolVersion) {
            this.address = address;
            this.protocolVersion = protocolVersion;
        }

        @Override
        public CompletionStage<Void> writeAndFlush(ResponseHandler handler, List<Message> messages) {
            Throwable failure = flushFailures.pollFirst();
            if (failure != null) {
                handler.onError(failure);
            } else {
                handler.onComplete();
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> write(List<Message> messages) {
            writeAttempts.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> forceClose(String reason) {
            return close();
        }

        @Override
        public CompletionStage<Void> close() {
            closeAttempts.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> setReadTimeout(Duration duration) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public BoltConnectionState state() {
            return null;
        }

        @Override
        public CompletionStage<AuthInfo> authInfo() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public String serverAgent() {
            return "Neo4j/Test";
        }

        @Override
        public BoltServerAddress serverAddress() {
            return address;
        }

        @Override
        public BoltProtocolVersion protocolVersion() {
            return protocolVersion;
        }

        @Override
        public boolean telemetrySupported() {
            return true;
        }

        @Override
        public boolean serverSideRoutingEnabled() {
            return true;
        }

        @Override
        public Optional<Duration> defaultReadTimeout() {
            return Optional.empty();
        }

        private void enqueueFlushFailure(Throwable failure) {
            flushFailures.addLast(failure);
        }

        private int closeAttempts() {
            return closeAttempts.get();
        }

        private int writeAttempts() {
            return writeAttempts.get();
        }
    }

    private static final class NoopLoggingProvider implements LoggingProvider {
        private static final System.Logger LOGGER = new NoopLogger();

        @Override
        public System.Logger getLog(Class<?> cls) {
            return LOGGER;
        }

        @Override
        public System.Logger getLog(String name) {
            return LOGGER;
        }
    }

    private static final class NoopLogger implements System.Logger {
        @Override
        public String getName() {
            return "noop";
        }

        @Override
        public boolean isLoggable(Level level) {
            return false;
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String msg, Throwable thrown) {
            // ignored
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String format, Object... params) {
            // ignored
        }
    }

}
