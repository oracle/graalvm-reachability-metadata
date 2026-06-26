/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_neo4j_bolt.neo4j_bolt_connection_pooled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.IntSupplier;

import javax.net.ssl.SSLContext;

import org.junit.jupiter.api.Test;
import org.neo4j.bolt.connection.AccessMode;
import org.neo4j.bolt.connection.AuthInfo;
import org.neo4j.bolt.connection.AuthToken;
import org.neo4j.bolt.connection.AuthTokens;
import org.neo4j.bolt.connection.BoltAgent;
import org.neo4j.bolt.connection.BoltConnection;
import org.neo4j.bolt.connection.BoltConnectionParameters;
import org.neo4j.bolt.connection.BoltConnectionProvider;
import org.neo4j.bolt.connection.BoltConnectionState;
import org.neo4j.bolt.connection.BoltProtocolVersion;
import org.neo4j.bolt.connection.BoltServerAddress;
import org.neo4j.bolt.connection.DatabaseName;
import org.neo4j.bolt.connection.ListenerEvent;
import org.neo4j.bolt.connection.LoggingProvider;
import org.neo4j.bolt.connection.MetricsListener;
import org.neo4j.bolt.connection.NotificationConfig;
import org.neo4j.bolt.connection.ResponseHandler;
import org.neo4j.bolt.connection.SecurityPlan;
import org.neo4j.bolt.connection.SecurityPlans;
import org.neo4j.bolt.connection.TelemetryApi;
import org.neo4j.bolt.connection.TransactionType;
import org.neo4j.bolt.connection.exception.BoltFailureException;
import org.neo4j.bolt.connection.exception.MinVersionAcquisitionException;
import org.neo4j.bolt.connection.message.BeginMessage;
import org.neo4j.bolt.connection.message.CommitMessage;
import org.neo4j.bolt.connection.message.DiscardMessage;
import org.neo4j.bolt.connection.message.LogoffMessage;
import org.neo4j.bolt.connection.message.LogonMessage;
import org.neo4j.bolt.connection.message.Message;
import org.neo4j.bolt.connection.message.Messages;
import org.neo4j.bolt.connection.message.PullMessage;
import org.neo4j.bolt.connection.message.ResetMessage;
import org.neo4j.bolt.connection.message.RollbackMessage;
import org.neo4j.bolt.connection.message.RouteMessage;
import org.neo4j.bolt.connection.message.RunMessage;
import org.neo4j.bolt.connection.message.TelemetryMessage;
import org.neo4j.bolt.connection.pooled.AuthTokenManager;
import org.neo4j.bolt.connection.pooled.PooledBoltConnectionSource;
import org.neo4j.bolt.connection.pooled.SecurityPlanSupplier;
import org.neo4j.bolt.connection.summary.ResetSummary;
import org.neo4j.bolt.connection.values.IsoDuration;
import org.neo4j.bolt.connection.values.Point;
import org.neo4j.bolt.connection.values.Type;
import org.neo4j.bolt.connection.values.Value;

public class Neo4j_bolt_connection_pooledTest {
    private static final int TEST_TIMEOUT_SECONDS = 5;
    private static final URI SERVER_URI = URI.create("bolt://127.0.0.1:7687");
    private static final BoltServerAddress ADDRESS = new BoltServerAddress(SERVER_URI);
    private static final String ROUTING_CONTEXT_ADDRESS = "neo4j://ignored.example.com";
    private static final BoltAgent AGENT = new BoltAgent("neo4j-bolt-pooled-test", "JVM", "Java", "JUnit");
    private static final String USER_AGENT = "pooled-test/1";
    private static final SecurityPlan SECURITY_PLAN = securityPlan();
    private static final DatabaseName DATABASE_NAME = DatabaseName.database("neo4j");
    private static final AuthToken AUTH_TOKEN = authToken("first-token");
    private static final AuthToken OTHER_AUTH_TOKEN = authToken("second-token");
    private static final BoltProtocolVersion BOLT_5_8 = new BoltProtocolVersion(5, 8);

    @Test
    void pooledProviderCreatesConnectionOnceAndReusesItAfterGracefulClose() throws Exception {
        MutableClock clock = new MutableClock(1_000L);
        RecordingMetricsListener metrics = new RecordingMetricsListener();
        RecordingConnectionProvider delegateProvider = new RecordingConnectionProvider(clock);
        PooledBoltConnectionSource provider = newProvider(delegateProvider, metrics, 2, 1_000L, 0L, -1L, clock);
        try {
            BoltConnection first = await(connect(provider, AUTH_TOKEN));
            FakeBoltConnection delegate = delegateProvider.connections().get(0);

            assertThat(first.serverAddress()).isEqualTo(ADDRESS);
            assertThat(first.serverAgent()).isEqualTo("Neo4j/5.26-test");
            assertThat(first.protocolVersion()).isEqualTo(BOLT_5_8);
            assertThat(first.telemetrySupported()).isTrue();
            assertThat(first.serverSideRoutingEnabled()).isTrue();
            assertThat(first.defaultReadTimeout()).contains(Duration.ofSeconds(30));
            assertThat(await(first.authInfo()).authToken()).isEqualTo(AUTH_TOKEN);
            assertThat(await(first.setReadTimeout(Duration.ofSeconds(3)))).isNull();
            await(first.write(Messages.run("RETURN 1", Map.of())));
            await(first.write(Messages.pull(10, -1)));
            await(first.write(Messages.telemetry(TelemetryApi.AUTO_COMMIT_TRANSACTION)));
            assertThat(delegate.operations())
                    .contains("run:RETURN 1", "pull:10:-1", "telemetry:AUTO_COMMIT_TRANSACTION");
            assertThat(delegate.lastReadTimeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(delegateProvider.connectCalls()).hasSize(1);
            assertThat(delegateProvider.connectCalls().get(0).uri()).isEqualTo(SERVER_URI);
            assertThat(delegateProvider.connectCalls().get(0).routingContextAddress())
                    .isEqualTo(ROUTING_CONTEXT_ADDRESS);
            assertThat(delegateProvider.connectCalls().get(0).userAgent()).isEqualTo(USER_AGENT);
            assertThat(metrics.inUse()).isEqualTo(1);
            assertThat(metrics.idle()).isZero();

            await(first.close());

            assertThat(delegate.closeCount()).isZero();
            assertThat(delegate.resetCount()).isEqualTo(1);
            assertThat(delegate.flushCount()).isEqualTo(1);
            assertThat(metrics.inUse()).isZero();
            assertThat(metrics.idle()).isEqualTo(1);

            BoltConnection second = await(connect(provider, AUTH_TOKEN));
            assertThat(second).isNotSameAs(first);
            assertThat(delegateProvider.connectCalls()).hasSize(1);
            assertThat(await(second.authInfo()).authToken()).isEqualTo(AUTH_TOKEN);
            assertThat(metrics.inUse()).isEqualTo(1);

            await(second.close());
            assertThat(metrics.events())
                    .contains("register", "beforeCreating", "afterCreated", "afterConnectionCreated",
                            "afterConnectionReleased");
        } finally {
            await(provider.close());
        }
    }

    @Test
    void pooledConnectionDelegatesRoutingAndTransactionOperations() throws Exception {
        MutableClock clock = new MutableClock(1_000L);
        RecordingConnectionProvider delegateProvider = new RecordingConnectionProvider(clock);
        PooledBoltConnectionSource provider = newProvider(
                delegateProvider, new RecordingMetricsListener(), 2, 1_000L, 0L, -1L, clock);
        Map<String, Value> parameters = Map.of("name", new SimpleValue("Ada"));
        Map<String, Value> metadata = Map.of("source", new SimpleValue("test"));

        try {
            BoltConnection connection = await(connect(provider, AUTH_TOKEN));
            FakeBoltConnection delegate = delegateProvider.connections().get(0);

            await(connection.write(Messages.route("neo4j", "neo4j-user", Set.of("bookmark-2"))));
            await(connection.write(Messages.beginTransaction(
                    "neo4j",
                    AccessMode.READ,
                    "neo4j-user",
                    Set.of("bookmark-2"),
                    TransactionType.UNCONSTRAINED,
                    Duration.ofSeconds(2),
                    metadata,
                    NotificationConfig.defaultConfig())));
            await(connection.write(Messages.run(
                    "neo4j",
                    AccessMode.WRITE,
                    "neo4j-user",
                    Set.of("bookmark-3"),
                    "MATCH (n) RETURN n",
                    parameters,
                    Duration.ofSeconds(3),
                    metadata,
                    NotificationConfig.defaultConfig())));
            await(connection.write(Messages.discard(5, 42)));
            await(connection.write(Messages.commit()));
            await(connection.write(Messages.rollback()));
            await(connection.write(Messages.reset()));

            assertThat(delegate.operations()).containsExactly(
                    "route",
                    "begin:UNCONSTRAINED",
                    "auto:MATCH (n) RETURN n",
                    "discard:5:42",
                    "commit",
                    "rollback",
                    "clear");

            await(connection.close());
        } finally {
            await(provider.close());
        }
    }

    @Test
    void pendingAcquisitionCompletesWhenTheOnlyConnectionIsReleased() throws Exception {
        MutableClock clock = new MutableClock(1_000L);
        RecordingConnectionProvider delegateProvider = new RecordingConnectionProvider(clock);
        PooledBoltConnectionSource provider = newProvider(
                delegateProvider, new RecordingMetricsListener(), 1, 1_000L, 0L, -1L, clock);

        try {
            BoltConnection first = await(connect(provider, AUTH_TOKEN));
            CompletionStage<BoltConnection> pending = connect(provider, AUTH_TOKEN);

            assertThat(pending.toCompletableFuture()).isNotDone();
            assertThat(delegateProvider.connectCalls()).hasSize(1);

            await(first.close());
            BoltConnection second = await(pending);

            assertThat(delegateProvider.connectCalls()).hasSize(1);
            assertThat(await(second.authInfo()).authToken()).isEqualTo(AUTH_TOKEN);
            await(second.close());
        } finally {
            await(provider.close());
        }
    }

    @Test
    void pendingAcquisitionTimesOutWhenPoolCapacityIsExhausted() throws Exception {
        MutableClock clock = new MutableClock(1_000L);
        RecordingMetricsListener metrics = new RecordingMetricsListener();
        RecordingConnectionProvider delegateProvider = new RecordingConnectionProvider(clock);
        PooledBoltConnectionSource provider = newProvider(delegateProvider, metrics, 1, 25L, 0L, -1L, clock);

        try {
            BoltConnection first = await(connect(provider, AUTH_TOKEN));
            CompletionStage<BoltConnection> pending = connect(provider, AUTH_TOKEN);

            assertThatThrownBy(() -> await(pending))
                    .isInstanceOf(ExecutionException.class)
                    .hasRootCauseInstanceOf(TimeoutException.class);
            assertThat(metrics.events()).contains("afterTimedOutToAcquireOrCreate");

            await(first.close());
        } finally {
            await(provider.close());
        }
    }

    @Test
    void closedOrErroredConnectionsArePurgedAndProviderRejectsNewAcquisitionsAfterClose() throws Exception {
        MutableClock clock = new MutableClock(1_000L);
        RecordingMetricsListener metrics = new RecordingMetricsListener();
        RecordingConnectionProvider delegateProvider = new RecordingConnectionProvider(clock);
        PooledBoltConnectionSource provider = newProvider(delegateProvider, metrics, 2, 1_000L, 0L, -1L, clock);

        BoltConnection first = await(connect(provider, AUTH_TOKEN));
        FakeBoltConnection firstDelegate = delegateProvider.connections().get(0);
        await(first.forceClose("test failure"));

        assertThat(firstDelegate.closeCount()).isGreaterThanOrEqualTo(1);
        assertThat(firstDelegate.forceCloseReasons()).containsExactly("test failure");
        assertThat(metrics.events()).contains("afterClosed");

        BoltConnection second = await(connect(provider, AUTH_TOKEN));
        FakeBoltConnection secondDelegate = delegateProvider.connections().get(1);
        assertThat(secondDelegate).isNotSameAs(firstDelegate);
        secondDelegate.state(BoltConnectionState.ERROR);
        await(second.close());
        assertThat(secondDelegate.closeCount()).isEqualTo(1);

        await(provider.close());
        assertThat(delegateProvider.closeCount()).isEqualTo(1);
        assertThat(metrics.events()).contains("removePoolMetrics");

        assertThatThrownBy(() -> await(connect(provider, AUTH_TOKEN)))
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Connection source is closed.");
    }

    @Test
    void expiredAuthenticationTriggersReauthOnReusableConnection() throws Exception {
        MutableClock clock = new MutableClock(1_000L);
        RecordingConnectionProvider delegateProvider = new RecordingConnectionProvider(clock);
        PooledBoltConnectionSource provider = newProvider(
                delegateProvider, new RecordingMetricsListener(), 2, 1_000L, 0L, -1L, clock);

        try {
            BoltConnection first = await(connect(provider, AUTH_TOKEN));
            FakeBoltConnection delegate = delegateProvider.connections().get(0);
            await(first.close());

            clock.advanceMillis(1_000L);
            provider.onExpired();
            BoltConnection second = await(connect(provider, OTHER_AUTH_TOKEN));

            assertThat(delegateProvider.connectCalls()).hasSize(1);
            assertThat(delegate.logoffCount()).isEqualTo(1);
            assertThat(delegate.logonTokens()).containsExactly(OTHER_AUTH_TOKEN);
            assertThat(await(second.authInfo()).authToken()).isEqualTo(OTHER_AUTH_TOKEN);

            await(second.close());
        } finally {
            await(provider.close());
        }
    }

    @Test
    void authorizationExpiredFlushErrorForcesReauthBeforeReuse() throws Exception {
        MutableClock clock = new MutableClock(1_000L);
        RecordingConnectionProvider delegateProvider = new RecordingConnectionProvider(clock);
        PooledBoltConnectionSource provider = newProvider(
                delegateProvider, new RecordingMetricsListener(), 2, 1_000L, 0L, -1L, clock);

        try {
            BoltConnection first = await(connect(provider, AUTH_TOKEN));
            FakeBoltConnection delegate = delegateProvider.connections().get(0);
            BoltFailureException authExpired = new BoltFailureException(
                    "Neo.ClientError.Security.AuthorizationExpired",
                    "authorization expired",
                    "50N42",
                    "authorization expired",
                    Map.of(),
                    null);
            RecordingResponseHandler responseHandler = new RecordingResponseHandler();

            delegate.nextFlushError(authExpired);
            await(first.writeAndFlush(responseHandler, Messages.run("RETURN 1", Map.of())));
            await(first.close());

            assertThat(responseHandler.errors()).containsExactly(authExpired);

            BoltConnection second = await(connect(provider, AUTH_TOKEN));

            assertThat(delegateProvider.connectCalls()).hasSize(1);
            assertThat(delegate.logoffCount()).isEqualTo(1);
            assertThat(delegate.logonTokens()).containsExactly(AUTH_TOKEN);
            assertThat(await(second.authInfo()).authToken()).isEqualTo(AUTH_TOKEN);

            await(second.close());
        } finally {
            await(provider.close());
        }
    }

    @Test
    void livenessChecksAndMaximumLifetimeProtectReusableConnections() throws Exception {
        MutableClock clock = new MutableClock(1_000L);
        RecordingConnectionProvider delegateProvider = new RecordingConnectionProvider(clock);
        PooledBoltConnectionSource provider = newProvider(
                delegateProvider, new RecordingMetricsListener(), 2, 1_000L, 1_000L, 10L, clock);

        try {
            BoltConnection first = await(connect(provider, AUTH_TOKEN));
            FakeBoltConnection firstDelegate = delegateProvider.connections().get(0);
            await(first.close());
            int resetCountAfterRelease = firstDelegate.resetCount();

            clock.advanceMillis(11L);
            BoltConnection second = await(connect(provider, AUTH_TOKEN));
            assertThat(delegateProvider.connectCalls()).hasSize(1);
            assertThat(firstDelegate.resetCount()).isEqualTo(resetCountAfterRelease + 1);
            await(second.close());

            clock.advanceMillis(1_001L);
            BoltConnection third = await(connect(provider, AUTH_TOKEN));
            FakeBoltConnection secondDelegate = delegateProvider.connections().get(1);
            assertThat(delegateProvider.connectCalls()).hasSize(2);
            assertThat(firstDelegate.closeCount()).isGreaterThanOrEqualTo(1);
            assertThat(secondDelegate).isNotSameAs(firstDelegate);

            await(third.close());
        } finally {
            await(provider.close());
        }
    }

    @Test
    void minProtocolVersionIsEnforcedForReusableConnections() throws Exception {
        MutableClock clock = new MutableClock(1_000L);
        RecordingConnectionProvider delegateProvider = new RecordingConnectionProvider(clock);
        PooledBoltConnectionSource provider = newProvider(
                delegateProvider, new RecordingMetricsListener(), 1, 0L, 0L, -1L, clock);

        try {
            BoltConnection first = await(connect(provider, AUTH_TOKEN));
            await(first.close());

            assertThatThrownBy(() -> await(connect(
                            provider,
                            AUTH_TOKEN,
                            new BoltProtocolVersion(6, 0))))
                    .isInstanceOf(ExecutionException.class)
                    .hasRootCauseInstanceOf(MinVersionAcquisitionException.class);

        } finally {
            await(provider.close());
        }
    }

    @Test
    void connectivityHelpersAcquireConnectionsAndReportProtocolCapabilities() throws Exception {
        MutableClock clock = new MutableClock(1_000L);
        RecordingConnectionProvider delegateProvider = new RecordingConnectionProvider(clock);
        PooledBoltConnectionSource provider = newProvider(
                delegateProvider, new RecordingMetricsListener(), 2, 1_000L, 0L, -1L, clock);

        try {
            await(provider.verifyConnectivity());
            assertThat(await(provider.supportsMultiDb())).isTrue();
            assertThat(await(provider.supportsSessionAuth())).isTrue();

            assertThat(delegateProvider.connectCalls()).hasSize(1);
            assertThat(delegateProvider.connections().get(0).resetCount()).isGreaterThanOrEqualTo(3);
        } finally {
            await(provider.close());
        }
    }

    private static CompletionStage<BoltConnection> connect(PooledBoltConnectionSource provider, AuthToken authToken) {
        return connect(provider, authToken, null);
    }

    private static CompletionStage<BoltConnection> connect(
            PooledBoltConnectionSource provider,
            AuthToken authToken,
            BoltProtocolVersion minVersion) {
        BoltConnectionParameters.Builder builder = BoltConnectionParameters.builder().withAuthToken(authToken);
        if (minVersion != null) {
            builder.withMinVersion(minVersion);
        }
        return provider.getConnection(builder.build());
    }

    private static PooledBoltConnectionSource newProvider(
            RecordingConnectionProvider delegateProvider,
            RecordingMetricsListener metrics,
            int maxSize,
            long acquisitionTimeoutMillis,
            long maxLifetimeMillis,
            long idleBeforeTestMillis,
            Clock clock) {
        return new PooledBoltConnectionSource(
                new TestLoggingProvider(),
                clock,
                SERVER_URI,
                delegateProvider,
                new TestAuthTokenManager(),
                new TestSecurityPlanSupplier(),
                maxSize,
                acquisitionTimeoutMillis,
                maxLifetimeMillis,
                idleBeforeTestMillis,
                metrics,
                ROUTING_CONTEXT_ADDRESS,
                AGENT,
                USER_AGENT,
                100,
                NotificationConfig.defaultConfig());
    }

    private static <T> T await(CompletionStage<T> stage)
            throws ExecutionException, InterruptedException, TimeoutException {
        return stage.toCompletableFuture().get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static AuthToken authToken(String name) {
        return AuthTokens.custom(Map.of("scheme", new SimpleValue(name)));
    }

    private static SecurityPlan securityPlan() {
        try {
            return SecurityPlans.encrypted(SSLContext.getDefault(), false);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Default SSL context is unavailable", e);
        }
    }

    private static final class TestAuthTokenManager implements AuthTokenManager {
        @Override
        public CompletionStage<AuthToken> getToken() {
            return CompletableFuture.completedStage(AUTH_TOKEN);
        }

        @Override
        public BoltFailureException handleBoltFailureException(AuthToken authToken, BoltFailureException exception) {
            return exception;
        }
    }

    private static final class TestSecurityPlanSupplier implements SecurityPlanSupplier {
        @Override
        public CompletionStage<SecurityPlan> getPlan() {
            return CompletableFuture.completedStage(SECURITY_PLAN);
        }
    }

    private static final class RecordingConnectionProvider implements BoltConnectionProvider {
        private final MutableClock clock;
        private final List<ConnectCall> connectCalls = new ArrayList<>();
        private final List<FakeBoltConnection> connections = new ArrayList<>();
        private int closeCount;

        private RecordingConnectionProvider(MutableClock clock) {
            this.clock = clock;
        }

        private List<ConnectCall> connectCalls() {
            return connectCalls;
        }

        private List<FakeBoltConnection> connections() {
            return connections;
        }

        private int closeCount() {
            return closeCount;
        }

        @Override
        public CompletionStage<BoltConnection> connect(
                URI uri,
                String routingContextAddress,
                BoltAgent boltAgent,
                String userAgent,
                int connectTimeoutMillis,
                SecurityPlan securityPlan,
                AuthToken authToken,
                BoltProtocolVersion minVersion,
                NotificationConfig notificationConfig) {
            ConnectCall call = new ConnectCall(uri, routingContextAddress, boltAgent, userAgent, connectTimeoutMillis,
                    securityPlan, authToken, minVersion, notificationConfig);
            connectCalls.add(call);
            FakeBoltConnection connection = new FakeBoltConnection(
                    new BoltServerAddress(uri), authToken, clock.millis());
            connections.add(connection);
            return CompletableFuture.completedStage(connection);
        }

        @Override
        public CompletionStage<Void> close() {
            closeCount++;
            return CompletableFuture.completedStage(null);
        }
    }

    private record ConnectCall(
            URI uri,
            String routingContextAddress,
            BoltAgent boltAgent,
            String userAgent,
            int connectTimeoutMillis,
            SecurityPlan securityPlan,
            AuthToken authToken,
            BoltProtocolVersion minVersion,
            NotificationConfig notificationConfig) {
    }

    private static final class FakeBoltConnection implements BoltConnection {
        private final BoltServerAddress serverAddress;
        private final List<String> operations = new ArrayList<>();
        private final List<String> forceCloseReasons = new ArrayList<>();
        private final List<AuthToken> logonTokens = new ArrayList<>();
        private BoltConnectionState state = BoltConnectionState.OPEN;
        private AuthInfo authInfo;
        private Duration lastReadTimeout;
        private Throwable nextFlushError;
        private int resetCount;
        private int flushCount;
        private int closeCount;
        private int logoffCount;

        private FakeBoltConnection(BoltServerAddress serverAddress, AuthToken authToken, long authAckMillis) {
            this.serverAddress = serverAddress;
            this.authInfo = new TestAuthInfo(authToken, authAckMillis);
        }

        private List<String> operations() {
            return operations;
        }

        private List<String> forceCloseReasons() {
            return forceCloseReasons;
        }

        private List<AuthToken> logonTokens() {
            return logonTokens;
        }

        private Duration lastReadTimeout() {
            return lastReadTimeout;
        }

        private int resetCount() {
            return resetCount;
        }

        private int flushCount() {
            return flushCount;
        }

        private int closeCount() {
            return closeCount;
        }

        private int logoffCount() {
            return logoffCount;
        }

        private void state(BoltConnectionState state) {
            this.state = state;
        }

        private void nextFlushError(Throwable nextFlushError) {
            this.nextFlushError = nextFlushError;
        }

        @Override
        public CompletionStage<Void> writeAndFlush(ResponseHandler handler, List<Message> messages) {
            recordMessages(messages);
            flushCount++;
            if (nextFlushError != null) {
                Throwable error = nextFlushError;
                nextFlushError = null;
                handler.onError(error);
                handler.onComplete();
                return CompletableFuture.completedStage(null);
            }
            handler.onResetSummary(new TestResetSummary());
            handler.onComplete();
            return CompletableFuture.completedStage(null);
        }

        @Override
        public CompletionStage<Void> write(List<Message> messages) {
            recordMessages(messages);
            return CompletableFuture.completedStage(null);
        }

        private void recordMessages(List<Message> messages) {
            for (Message message : messages) {
                if (message instanceof RouteMessage) {
                    operations.add("route");
                } else if (message instanceof BeginMessage beginMessage) {
                    operations.add("begin:" + beginMessage.transactionType());
                } else if (message instanceof RunMessage runMessage) {
                    if (runMessage.extra().isPresent()) {
                        operations.add("auto:" + runMessage.query());
                    } else {
                        operations.add("run:" + runMessage.query());
                    }
                } else if (message instanceof PullMessage pullMessage) {
                    operations.add("pull:" + pullMessage.qid() + ":" + pullMessage.request());
                } else if (message instanceof DiscardMessage discardMessage) {
                    operations.add("discard:" + discardMessage.qid() + ":" + discardMessage.number());
                } else if (message instanceof CommitMessage) {
                    operations.add("commit");
                } else if (message instanceof RollbackMessage) {
                    operations.add("rollback");
                } else if (message instanceof ResetMessage) {
                    resetCount++;
                    operations.add("clear");
                } else if (message instanceof LogoffMessage) {
                    logoffCount++;
                    operations.add("logoff");
                } else if (message instanceof LogonMessage logonMessage) {
                    AuthToken authToken = logonMessage.authToken();
                    logonTokens.add(authToken);
                    authInfo = new TestAuthInfo(authToken, System.currentTimeMillis());
                    operations.add("logon");
                } else if (message instanceof TelemetryMessage telemetryMessage) {
                    operations.add("telemetry:" + telemetryMessage.api());
                }
            }
        }

        @Override
        public CompletionStage<Void> forceClose(String reason) {
            forceCloseReasons.add(reason);
            state = BoltConnectionState.CLOSED;
            return close();
        }

        @Override
        public CompletionStage<Void> close() {
            closeCount++;
            state = BoltConnectionState.CLOSED;
            return CompletableFuture.completedStage(null);
        }

        @Override
        public CompletionStage<Void> setReadTimeout(Duration duration) {
            lastReadTimeout = duration;
            return CompletableFuture.completedStage(null);
        }

        @Override
        public BoltConnectionState state() {
            return state;
        }

        @Override
        public CompletionStage<AuthInfo> authInfo() {
            return CompletableFuture.completedStage(authInfo);
        }

        @Override
        public String serverAgent() {
            return "Neo4j/5.26-test";
        }

        @Override
        public BoltServerAddress serverAddress() {
            return serverAddress;
        }

        @Override
        public BoltProtocolVersion protocolVersion() {
            return BOLT_5_8;
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
            return Optional.of(Duration.ofSeconds(30));
        }
    }

    private record TestAuthInfo(AuthToken authToken, long authAckMillis) implements AuthInfo {
    }

    private record TestResetSummary() implements ResetSummary {
    }

    private static final class RecordingResponseHandler implements ResponseHandler {
        private final List<Throwable> errors = new ArrayList<>();

        private List<Throwable> errors() {
            return errors;
        }

        @Override
        public void onError(Throwable throwable) {
            errors.add(throwable);
        }
    }

    private record SimpleValue(String value) implements Value {
        @Override
        public Type type() {
            return Type.STRING;
        }

        @Override
        public boolean asBoolean() {
            throw new UnsupportedOperationException("Boolean values are not used by these tests");
        }

        @Override
        public byte[] asByteArray() {
            throw new UnsupportedOperationException("Byte values are not used by these tests");
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public long asLong() {
            throw new UnsupportedOperationException("Integer values are not used by these tests");
        }

        @Override
        public double asDouble() {
            throw new UnsupportedOperationException("Float values are not used by these tests");
        }

        @Override
        public LocalDate asLocalDate() {
            throw new UnsupportedOperationException("Date values are not used by these tests");
        }

        @Override
        public OffsetTime asOffsetTime() {
            throw new UnsupportedOperationException("Time values are not used by these tests");
        }

        @Override
        public LocalTime asLocalTime() {
            throw new UnsupportedOperationException("Time values are not used by these tests");
        }

        @Override
        public LocalDateTime asLocalDateTime() {
            throw new UnsupportedOperationException("Date time values are not used by these tests");
        }

        @Override
        public ZonedDateTime asZonedDateTime() {
            throw new UnsupportedOperationException("Date time values are not used by these tests");
        }

        @Override
        public IsoDuration asIsoDuration() {
            throw new UnsupportedOperationException("Duration values are not used by these tests");
        }

        @Override
        public Point asPoint() {
            throw new UnsupportedOperationException("Point values are not used by these tests");
        }

        @Override
        public boolean isNull() {
            return false;
        }

        @Override
        public boolean isEmpty() {
            return value.isEmpty();
        }

        @Override
        public Iterable<String> keys() {
            return Collections.emptyList();
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public Value get(String key) {
            return null;
        }

        @Override
        public Iterable<Value> values() {
            return Collections.emptyList();
        }

        @Override
        public boolean containsKey(String key) {
            return false;
        }

        @Override
        public <T> Map<String, T> asMap(Function<Value, T> mapFunction) {
            return Collections.emptyMap();
        }
    }

    private static final class RecordingMetricsListener implements MetricsListener {
        private final List<String> events = new ArrayList<>();
        private IntSupplier inUse = () -> 0;
        private IntSupplier idle = () -> 0;

        private List<String> events() {
            return events;
        }

        private int inUse() {
            return inUse.getAsInt();
        }

        private int idle() {
            return idle.getAsInt();
        }

        @Override
        public void beforeCreating(String id, ListenerEvent<?> event) {
            events.add("beforeCreating");
        }

        @Override
        public void afterCreated(String id, ListenerEvent<?> event) {
            events.add("afterCreated");
        }

        @Override
        public void afterFailedToCreate(String id) {
            events.add("afterFailedToCreate");
        }

        @Override
        public void afterClosed(String id) {
            events.add("afterClosed");
        }

        @Override
        public void beforeAcquiringOrCreating(String id, ListenerEvent<?> event) {
            events.add("beforeAcquiringOrCreating");
        }

        @Override
        public void afterAcquiringOrCreating(String id) {
            events.add("afterAcquiringOrCreating");
        }

        @Override
        public void afterAcquiredOrCreated(String id, ListenerEvent<?> event) {
            events.add("afterAcquiredOrCreated");
        }

        @Override
        public void afterTimedOutToAcquireOrCreate(String id) {
            events.add("afterTimedOutToAcquireOrCreate");
        }

        @Override
        public void afterConnectionCreated(String id, ListenerEvent<?> event) {
            events.add("afterConnectionCreated");
        }

        @Override
        public void afterConnectionReleased(String id, ListenerEvent<?> event) {
            events.add("afterConnectionReleased");
        }

        @Override
        public ListenerEvent<?> createListenerEvent() {
            return new TestListenerEvent();
        }

        @Override
        public void registerPoolMetrics(
                String id,
                BoltServerAddress address,
                IntSupplier inUse,
                IntSupplier idle) {
            events.add("register");
            this.inUse = inUse;
            this.idle = idle;
        }

        @Override
        public void removePoolMetrics(String id) {
            events.add("removePoolMetrics");
        }
    }

    private static final class TestListenerEvent implements ListenerEvent<Integer> {
        private final AtomicInteger starts = new AtomicInteger();

        @Override
        public void start() {
            starts.incrementAndGet();
        }

        @Override
        public Integer getSample() {
            return starts.get();
        }
    }

    private static final class TestLoggingProvider implements LoggingProvider {
        @Override
        public System.Logger getLog(Class<?> clazz) {
            return System.getLogger(clazz.getName());
        }

        @Override
        public System.Logger getLog(String name) {
            return System.getLogger(name);
        }
    }

    private static final class MutableClock extends Clock {
        private long millis;

        private MutableClock(long millis) {
            this.millis = millis;
        }

        private void advanceMillis(long delta) {
            millis += delta;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public long millis() {
            return millis;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }
    }
}
