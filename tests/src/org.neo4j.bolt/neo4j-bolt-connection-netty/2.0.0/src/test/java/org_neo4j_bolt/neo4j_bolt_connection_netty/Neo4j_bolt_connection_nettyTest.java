/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_neo4j_bolt.neo4j_bolt_connection_netty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.IntSupplier;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.jupiter.api.Test;
import org.neo4j.bolt.connection.AccessMode;
import org.neo4j.bolt.connection.AuthInfo;
import org.neo4j.bolt.connection.AuthTokens;
import org.neo4j.bolt.connection.BasicResponseHandler;
import org.neo4j.bolt.connection.BoltAgent;
import org.neo4j.bolt.connection.BoltConnection;
import org.neo4j.bolt.connection.BoltConnectionState;
import org.neo4j.bolt.connection.BoltProtocolVersion;
import org.neo4j.bolt.connection.BoltServerAddress;
import org.neo4j.bolt.connection.ClusterComposition;
import org.neo4j.bolt.connection.DatabaseName;
import org.neo4j.bolt.connection.DatabaseNameUtil;
import org.neo4j.bolt.connection.DefaultDomainNameResolver;
import org.neo4j.bolt.connection.ListenerEvent;
import org.neo4j.bolt.connection.LoggingProvider;
import org.neo4j.bolt.connection.MetricsListener;
import org.neo4j.bolt.connection.NotificationConfig;
import org.neo4j.bolt.connection.RoutingContext;
import org.neo4j.bolt.connection.SecurityPlans;
import org.neo4j.bolt.connection.TelemetryApi;
import org.neo4j.bolt.connection.TransactionType;
import org.neo4j.bolt.connection.exception.BoltClientException;
import org.neo4j.bolt.connection.netty.BootstrapFactory;
import org.neo4j.bolt.connection.netty.NettyBoltConnectionProvider;
import org.neo4j.bolt.connection.summary.CommitSummary;
import org.neo4j.bolt.connection.summary.RouteSummary;
import org.neo4j.bolt.connection.summary.RunSummary;
import org.neo4j.bolt.connection.values.IsoDuration;
import org.neo4j.bolt.connection.values.Node;
import org.neo4j.bolt.connection.values.Path;
import org.neo4j.bolt.connection.values.Point;
import org.neo4j.bolt.connection.values.Relationship;
import org.neo4j.bolt.connection.values.Segment;
import org.neo4j.bolt.connection.values.Type;
import org.neo4j.bolt.connection.values.Value;
import org.neo4j.bolt.connection.values.ValueFactory;

public class Neo4j_bolt_connection_nettyTest {
    private static final int BOLT_MAGIC = 0x6060B017;
    private static final int BOLT_5_8 = 0x00000805;
    private static final int CONNECT_TIMEOUT_MILLIS = 2_000;
    private static final int TEST_TIMEOUT_SECONDS = 5;
    private static final SimpleValueFactory VALUE_FACTORY = new SimpleValueFactory();
    private static final BoltAgent AGENT = new BoltAgent("neo4j-bolt-netty-test", "JVM", "Java", "JUnit");
    private static final LoggingProvider LOGGING = new TestLoggingProvider();
    private static final MetricsListener METRICS = new TestMetricsListener();

    @Test
    void bootstrapFactoryCreatesReusableNettyBootstraps() {
        Bootstrap ownedBootstrap = BootstrapFactory.newBootstrap(1);
        EventLoopGroup ownedGroup = ownedBootstrap.config().group();
        try {
            assertThat(ownedGroup).isNotNull();
            assertThat(ownedBootstrap.config().channelFactory()).isNotNull();
        } finally {
            ownedGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        }

        EventLoopGroup suppliedGroup = new NioEventLoopGroup(1);
        try {
            Bootstrap suppliedBootstrap = BootstrapFactory.newBootstrap(suppliedGroup);

            assertThat(suppliedBootstrap.config().group()).isSameAs(suppliedGroup);
            assertThat(suppliedBootstrap.config().channelFactory()).isNotNull();
        } finally {
            suppliedGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    @Test
    void providerConnectsToBoltServerAndHandlesQueryTransactionAuthAndTelemetryMessages() throws Exception {
        try (BoltTestServer server = new BoltTestServer(BOLT_5_8);
                TestProvider provider = new TestProvider()) {
            BoltConnection connection = await(provider.connect(server.address()));

            assertThat(connection.state()).isEqualTo(BoltConnectionState.OPEN);
            assertThat(connection.serverAgent()).isEqualTo("Neo4j/5.26-test");
            assertThat(connection.serverAddress()).isEqualTo(server.address());
            assertThat(connection.protocolVersion()).isEqualTo(new BoltProtocolVersion(5, 8));
            assertThat(connection.telemetrySupported()).isTrue();
            assertThat(connection.serverSideRoutingEnabled()).isTrue();
            assertThat(connection.defaultReadTimeout()).contains(Duration.ofSeconds(7));

            AuthInfo authInfo = await(connection.authInfo());
            assertThat(authInfo.authToken().asMap().get("scheme").asString()).isEqualTo("none");
            assertThat(authInfo.authAckMillis()).isGreaterThan(0L);

            BasicResponseHandler queryHandler = new BasicResponseHandler();
            await(connection.run("RETURN $name", Map.of("name", VALUE_FACTORY.value("Alice")))
                    .thenCompose(c -> c.pull(1, -1))
                    .thenCompose(c -> c.flush(queryHandler)));
            BasicResponseHandler.Summaries querySummaries = await(queryHandler.summaries());
            RunSummary runSummary = querySummaries.runSummary();
            assertThat(runSummary.queryId()).isEqualTo(42L);
            assertThat(runSummary.keys()).containsExactly("name");
            assertThat(runSummary.resultAvailableAfter()).isEqualTo(3L);
            assertThat(runSummary.databaseName()).contains("neo4j");
            assertThat(querySummaries.valuesList()).hasSize(1);
            assertThat(querySummaries.valuesList().get(0)[0].asString()).isEqualTo("Alice");
            assertThat(querySummaries.pullSummary().hasMore()).isFalse();
            assertThat(querySummaries.pullSummary().metadata().get("type").asString()).isEqualTo("r");

            BasicResponseHandler transactionHandler = new BasicResponseHandler();
            await(connection.beginTransaction(
                            DatabaseNameUtil.database("neo4j"),
                            AccessMode.WRITE,
                            "bm-1",
                            Set.of("neo4j"),
                            TransactionType.DEFAULT,
                            Duration.ofSeconds(1),
                            Map.of("purpose", VALUE_FACTORY.value("integration-test")),
                            null,
                            NotificationConfig.defaultConfig())
                    .thenCompose(c -> c.commit())
                    .thenCompose(c -> c.flush(transactionHandler)));
            BasicResponseHandler.Summaries transactionSummaries = await(transactionHandler.summaries());
            assertThat(transactionSummaries.beginSummary().databaseName()).contains("neo4j");
            CommitSummary commitSummary = transactionSummaries.commitSummary();
            assertThat(commitSummary.bookmark()).contains("bm-after-commit");

            BasicResponseHandler authAndTelemetryHandler = new BasicResponseHandler();
            await(connection.telemetry(TelemetryApi.AUTO_COMMIT_TRANSACTION)
                    .thenCompose(BoltConnection::logoff)
                    .thenCompose(c -> c.logon(AuthTokens.none(VALUE_FACTORY)))
                    .thenCompose(c -> c.flush(authAndTelemetryHandler)));
            BasicResponseHandler.Summaries authAndTelemetrySummaries = await(authAndTelemetryHandler.summaries());
            assertThat(authAndTelemetrySummaries.telemetrySummary()).isNotNull();
            AuthInfo refreshedAuthInfo = await(connection.authInfo());
            assertThat(refreshedAuthInfo.authToken().asMap().get("scheme").asString()).isEqualTo("none");

            await(connection.close());
            assertThat(connection.state()).isEqualTo(BoltConnectionState.CLOSED);
            server.awaitHandled();
            assertThat(server.signatures()).contains(0x01, 0x6A, 0x10, 0x3F, 0x11, 0x12, 0x54, 0x6B);
        }
    }

    @Test
    void connectionRunsAutoCommitTransactionAndDiscardsResultStream() throws Exception {
        try (BoltTestServer server = new BoltTestServer(BOLT_5_8);
                TestProvider provider = new TestProvider()) {
            BoltConnection connection = await(provider.connect(server.address()));
            BasicResponseHandler handler = new BasicResponseHandler();

            await(connection.runInAutoCommitTransaction(
                            DatabaseNameUtil.database("neo4j"),
                            AccessMode.READ,
                            null,
                            Set.of("bm-before-auto-commit"),
                            "RETURN $name",
                            Map.of("name", VALUE_FACTORY.value("Bob")),
                            Duration.ofSeconds(2),
                            Map.of("purpose", VALUE_FACTORY.value("auto-commit-test")),
                            NotificationConfig.defaultConfig())
                    .thenCompose(c -> c.discard(1, -1))
                    .thenCompose(c -> c.flush(handler)));
            BasicResponseHandler.Summaries summaries = await(handler.summaries());

            assertThat(summaries.runSummary().queryId()).isEqualTo(42L);
            assertThat(summaries.discardSummary()).isNotNull();
            assertThat(summaries.discardSummary().metadata().get("has_more").asBoolean()).isFalse();
            assertThat(summaries.valuesList()).isEmpty();

            await(connection.close());
            server.awaitHandled();
            assertThat(server.signatures()).containsSubsequence(0x01, 0x10, 0x2F);
        }
    }

    @Test
    void connectionRouteDiscoversClusterComposition() throws Exception {
        try (BoltTestServer server = new BoltTestServer(BOLT_5_8);
                TestProvider provider = new TestProvider()) {
            BoltConnection connection = await(provider.connect(server.address()));
            BasicResponseHandler handler = new BasicResponseHandler();

            await(connection.route(DatabaseNameUtil.database("neo4j"), null, Set.of("bm-before-route"))
                    .thenCompose(c -> c.flush(handler)));
            BasicResponseHandler.Summaries summaries = await(handler.summaries());
            RouteSummary routeSummary = summaries.routeSummary();
            ClusterComposition clusterComposition = routeSummary.clusterComposition();

            assertThat(clusterComposition.databaseName()).isEqualTo("neo4j");
            assertThat(clusterComposition.hasWriters()).isTrue();
            assertThat(clusterComposition.hasRoutersAndReaders()).isTrue();
            assertThat(clusterComposition.expirationTimestamp()).isGreaterThan(System.currentTimeMillis());
            assertThat(clusterComposition.readers())
                    .containsExactly(new BoltServerAddress("reader.example.com", 9001));
            assertThat(clusterComposition.writers())
                    .containsExactly(new BoltServerAddress("writer.example.com", 9002));
            assertThat(clusterComposition.routers())
                    .containsExactlyInAnyOrder(
                            new BoltServerAddress("router-a.example.com", 9003),
                            new BoltServerAddress("router-b.example.com", 9004));

            await(connection.close());
            server.awaitHandled();
            assertThat(server.signatures()).contains(0x66);
        }
    }

    @Test
    void providerConnectivityFeatureChecksUseHandshakeAndAuthentication() throws Exception {
        try (BoltTestServer server = new BoltTestServer(BOLT_5_8);
                TestProvider provider = new TestProvider()) {
            await(provider.provider().verifyConnectivity(
                    server.address(),
                    RoutingContext.EMPTY,
                    AGENT,
                    "bolt",
                    CONNECT_TIMEOUT_MILLIS,
                    SecurityPlans.unencrypted(),
                    AuthTokens.none(VALUE_FACTORY)));
            server.awaitHandled();
            assertThat(server.signatures()).contains(0x01, 0x6A);
        }

        try (BoltTestServer server = new BoltTestServer(BOLT_5_8);
                TestProvider provider = new TestProvider()) {
            Boolean supportsMultiDb = await(provider.provider().supportsMultiDb(
                    server.address(),
                    RoutingContext.EMPTY,
                    AGENT,
                    "bolt",
                    CONNECT_TIMEOUT_MILLIS,
                    SecurityPlans.unencrypted(),
                    AuthTokens.none(VALUE_FACTORY)));

            assertThat(supportsMultiDb).isTrue();
            server.awaitHandled();
        }

        try (BoltTestServer server = new BoltTestServer(BOLT_5_8);
                TestProvider provider = new TestProvider()) {
            Boolean supportsSessionAuth = await(provider.provider().supportsSessionAuth(
                    server.address(),
                    RoutingContext.EMPTY,
                    AGENT,
                    "bolt",
                    CONNECT_TIMEOUT_MILLIS,
                    SecurityPlans.unencrypted(),
                    AuthTokens.none(VALUE_FACTORY)));

            assertThat(supportsSessionAuth).isTrue();
            server.awaitHandled();
        }
    }

    @Test
    void providerReportsUnsupportedBoltProtocolNegotiation() throws Exception {
        try (BoltTestServer server = new BoltTestServer(0);
                TestProvider provider = new TestProvider()) {
            CompletionStage<Void> verification = provider.provider().verifyConnectivity(
                    server.address(),
                    RoutingContext.EMPTY,
                    AGENT,
                    "bolt",
                    CONNECT_TIMEOUT_MILLIS,
                    SecurityPlans.unencrypted(),
                    AuthTokens.none(VALUE_FACTORY));

            assertThatThrownBy(() -> await(verification))
                    .isInstanceOf(ExecutionException.class)
                    .hasRootCauseInstanceOf(BoltClientException.class);
            server.awaitHandled();
            assertThat(server.signatures()).isEmpty();
        }
    }

    private static <T> T await(CompletionStage<T> stage)
            throws ExecutionException, InterruptedException, TimeoutException {
        return stage.toCompletableFuture().get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static final class TestProvider implements AutoCloseable {
        private final EventLoopGroup eventLoopGroup;
        private final NettyBoltConnectionProvider provider;

        private TestProvider() {
            this.eventLoopGroup = new NioEventLoopGroup(1);
            this.provider = new NettyBoltConnectionProvider(
                    eventLoopGroup,
                    Clock.systemUTC(),
                    DefaultDomainNameResolver.getInstance(),
                    null,
                    LOGGING,
                    VALUE_FACTORY,
                    METRICS);
        }

        private CompletionStage<BoltConnection> connect(BoltServerAddress address) {
            DatabaseName databaseName = DatabaseNameUtil.defaultDatabase();
            return provider.connect(
                    address,
                    RoutingContext.EMPTY,
                    AGENT,
                    "bolt",
                    CONNECT_TIMEOUT_MILLIS,
                    SecurityPlans.unencrypted(),
                    databaseName,
                    () -> CompletableFuture.completedFuture(AuthTokens.none(VALUE_FACTORY)),
                    AccessMode.WRITE,
                    Set.of("neo4j"),
                    null,
                    null,
                    NotificationConfig.defaultConfig(),
                    selectedDatabase -> { },
                    Map.of("source", "test"));
        }

        private NettyBoltConnectionProvider provider() {
            return provider;
        }

        @Override
        public void close() throws Exception {
            try {
                await(provider.close());
            } finally {
                eventLoopGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
            }
        }
    }

    private static final class BoltTestServer implements AutoCloseable {
        private final int selectedProtocol;
        private final ServerSocket serverSocket;
        private final ExecutorService executor;
        private final CompletableFuture<Void> handled;
        private final AtomicReference<Throwable> failure;
        private final List<Integer> signatures;

        private BoltTestServer(int selectedProtocol) throws IOException {
            this.selectedProtocol = selectedProtocol;
            this.serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            this.executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "bolt-test-server");
                thread.setDaemon(true);
                return thread;
            });
            this.handled = new CompletableFuture<>();
            this.failure = new AtomicReference<>();
            this.signatures = Collections.synchronizedList(new ArrayList<>());
            executor.execute(this::serveOneConnection);
        }

        private BoltServerAddress address() {
            return new BoltServerAddress(serverSocket.getInetAddress().getHostAddress(), serverSocket.getLocalPort());
        }

        private List<Integer> signatures() {
            return List.copyOf(signatures);
        }

        private void awaitHandled() throws Exception {
            handled.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (failure.get() != null) {
                throw new AssertionError("Bolt test server failed", failure.get());
            }
        }

        private void serveOneConnection() {
            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout((int) Duration.ofSeconds(TEST_TIMEOUT_SECONDS).toMillis());
                DataInputStream input = new DataInputStream(socket.getInputStream());
                OutputStream output = socket.getOutputStream();
                byte[] handshake = input.readNBytes(20);
                if (handshake.length != 20 || readInt(handshake, 0) != BOLT_MAGIC) {
                    throw new IOException("Invalid Bolt handshake");
                }
                writeInt(output, selectedProtocol);
                output.flush();
                if (selectedProtocol == 0) {
                    return;
                }

                while (!socket.isClosed()) {
                    byte[] payload = readChunkedMessage(input);
                    if (payload == null) {
                        return;
                    }
                    int signature = messageSignature(payload);
                    signatures.add(signature);
                    if (signature == 0x02) {
                        return;
                    }
                    respond(signature, output);
                    output.flush();
                }
            } catch (SocketTimeoutException e) {
                failure.compareAndSet(null, e);
            } catch (Throwable e) {
                if (!serverSocket.isClosed()) {
                    failure.compareAndSet(null, e);
                }
            } finally {
                handled.complete(null);
            }
        }

        private void respond(int signature, OutputStream output) throws IOException {
            switch (signature) {
                case 0x01 -> writeSuccess(output, Map.of(
                        "server", "Neo4j/5.26-test",
                        "connection_id", "bolt-1",
                        "hints", Map.of(
                                "connection.recv_timeout_seconds", 7L,
                                "telemetry.enabled", true,
                                "ssr.enabled", true)));
                case 0x10 -> writeSuccess(output, Map.of(
                        "fields", List.of("name"),
                        "qid", 42L,
                        "t_first", 3L,
                        "db", "neo4j"));
                case 0x3F -> {
                    writeRecord(output, List.of("Alice"));
                    writeSuccess(output, Map.of("has_more", false, "type", "r"));
                }
                case 0x2F -> writeSuccess(output, Map.of("has_more", false));
                case 0x11 -> writeSuccess(output, Map.of("db", "neo4j"));
                case 0x12 -> writeSuccess(output, Map.of("bookmark", "bm-after-commit"));
                case 0x66 -> writeSuccess(output, Map.of("rt", routeTable()));
                default -> writeSuccess(output, Map.of());
            }
        }

        private static Map<String, Object> routeTable() {
            return Map.of(
                    "ttl", 300L,
                    "db", "neo4j",
                    "servers", List.of(
                            Map.of(
                                    "role", "READ",
                                    "addresses", List.of("reader.example.com:9001")),
                            Map.of(
                                    "role", "WRITE",
                                    "addresses", List.of("writer.example.com:9002")),
                            Map.of(
                                    "role", "ROUTE",
                                    "addresses", List.of(
                                            "router-a.example.com:9003",
                                            "router-b.example.com:9004"))));
        }

        private static byte[] readChunkedMessage(DataInputStream input) throws IOException {
            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            while (true) {
                int size;
                try {
                    size = input.readUnsignedShort();
                } catch (EOFException e) {
                    return null;
                } catch (SocketTimeoutException e) {
                    return null;
                }
                if (size == 0) {
                    if (payload.size() == 0) {
                        continue;
                    }
                    return payload.toByteArray();
                }
                payload.write(input.readNBytes(size));
            }
        }

        private static int messageSignature(byte[] payload) {
            if (payload.length < 2 || (payload[0] & 0xF0) != 0xB0) {
                return -1;
            }
            return payload[1] & 0xFF;
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            executor.shutdownNow();
            executor.awaitTermination(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private static void writeSuccess(OutputStream output, Map<String, Object> metadata) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(0xB1);
        payload.write(0x70);
        writeValue(payload, metadata);
        writeMessage(output, payload.toByteArray());
    }

    private static void writeRecord(OutputStream output, List<Object> fields) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(0xB1);
        payload.write(0x71);
        writeValue(payload, fields);
        writeMessage(output, payload.toByteArray());
    }

    private static void writeMessage(OutputStream output, byte[] payload) throws IOException {
        output.write((payload.length >>> 8) & 0xFF);
        output.write(payload.length & 0xFF);
        output.write(payload);
        output.write(0);
        output.write(0);
    }

    private static void writeValue(OutputStream output, Object value) throws IOException {
        if (value == null) {
            output.write(0xC0);
        } else if (value instanceof Boolean booleanValue) {
            output.write(booleanValue ? 0xC3 : 0xC2);
        } else if (value instanceof Number numberValue) {
            writeLong(output, numberValue.longValue());
        } else if (value instanceof String stringValue) {
            writeString(output, stringValue);
        } else if (value instanceof List<?> listValue) {
            writeList(output, listValue);
        } else if (value instanceof Map<?, ?> mapValue) {
            writeMap(output, mapValue);
        } else {
            throw new IOException("Unsupported PackStream value: " + value.getClass().getName());
        }
    }

    private static void writeLong(OutputStream output, long value) throws IOException {
        if (value >= -16 && value <= 127) {
            output.write((int) value & 0xFF);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            output.write(0xC8);
            output.write((int) value & 0xFF);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            output.write(0xC9);
            output.write((int) (value >>> 8) & 0xFF);
            output.write((int) value & 0xFF);
        } else {
            output.write(0xCB);
            for (int shift = 56; shift >= 0; shift -= 8) {
                output.write((int) (value >>> shift) & 0xFF);
            }
        }
    }

    private static void writeString(OutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length < 16) {
            output.write(0x80 | bytes.length);
        } else {
            output.write(0xD0);
            output.write(bytes.length);
        }
        output.write(bytes);
    }

    private static void writeList(OutputStream output, List<?> values) throws IOException {
        if (values.size() >= 16) {
            throw new IOException("Large lists are not needed by this test server");
        }
        output.write(0x90 | values.size());
        for (Object value : values) {
            writeValue(output, value);
        }
    }

    private static void writeMap(OutputStream output, Map<?, ?> values) throws IOException {
        if (values.size() >= 16) {
            throw new IOException("Large maps are not needed by this test server");
        }
        output.write(0xA0 | values.size());
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            writeString(output, (String) entry.getKey());
            writeValue(output, entry.getValue());
        }
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private static void writeInt(OutputStream output, int value) throws IOException {
        output.write((value >>> 24) & 0xFF);
        output.write((value >>> 16) & 0xFF);
        output.write((value >>> 8) & 0xFF);
        output.write(value & 0xFF);
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

    private static final class TestMetricsListener implements MetricsListener {
        @Override
        public void beforeCreating(String id, ListenerEvent<?> event) {
        }

        @Override
        public void afterCreated(String id, ListenerEvent<?> event) {
        }

        @Override
        public void afterFailedToCreate(String id) {
        }

        @Override
        public void afterClosed(String id) {
        }

        @Override
        public void beforeAcquiringOrCreating(String id, ListenerEvent<?> event) {
        }

        @Override
        public void afterAcquiringOrCreating(String id) {
        }

        @Override
        public void afterAcquiredOrCreated(String id, ListenerEvent<?> event) {
        }

        @Override
        public void afterTimedOutToAcquireOrCreate(String id) {
        }

        @Override
        public void afterConnectionCreated(String id, ListenerEvent<?> event) {
        }

        @Override
        public void afterConnectionReleased(String id, ListenerEvent<?> event) {
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
        }

        @Override
        public void removePoolMetrics(String id) {
        }
    }

    private static final class TestListenerEvent implements ListenerEvent<Void> {
        @Override
        public void start() {
        }

        @Override
        public Void getSample() {
            return null;
        }
    }

    private static final class SimpleValueFactory implements ValueFactory {
        @Override
        public Value value(Object value) {
            if (value == null) {
                return new SimpleValue(Type.NULL, null);
            }
            if (value instanceof Value boltValue) {
                return boltValue;
            }
            if (value instanceof Boolean booleanValue) {
                return new SimpleValue(Type.BOOLEAN, booleanValue);
            }
            if (value instanceof Number numberValue) {
                if (value instanceof Float || value instanceof Double) {
                    return new SimpleValue(Type.FLOAT, numberValue.doubleValue());
                }
                return new SimpleValue(Type.INTEGER, numberValue.longValue());
            }
            if (value instanceof String stringValue) {
                return new SimpleValue(Type.STRING, stringValue);
            }
            if (value instanceof byte[] bytes) {
                return new SimpleValue(Type.BYTES, bytes.clone());
            }
            if (value instanceof Map<?, ?> mapValue) {
                return new SimpleValue(Type.MAP, new LinkedHashMap<>(toValueMap(mapValue)));
            }
            if (value instanceof Collection<?> collectionValue) {
                return new SimpleValue(Type.LIST, collectionValue.stream().map(this::value).toList());
            }
            if (value instanceof Value[] values) {
                return new SimpleValue(Type.LIST, List.of(values));
            }
            throw new IllegalArgumentException("Unsupported value type: " + value.getClass().getName());
        }

        @Override
        public Node node(long id, String elementId, Collection<String> labels, Map<String, Value> properties) {
            throw new UnsupportedOperationException("Nodes are not used by these tests");
        }

        @Override
        public Relationship relationship(
                long id,
                String elementId,
                long startNodeId,
                String startNodeElementId,
                long endNodeId,
                String endNodeElementId,
                String type,
                Map<String, Value> properties) {
            throw new UnsupportedOperationException("Relationships are not used by these tests");
        }

        @Override
        public Segment segment(Node start, Relationship relationship, Node end) {
            throw new UnsupportedOperationException("Segments are not used by these tests");
        }

        @Override
        public Path path(List<Segment> segments, List<Node> nodes, List<Relationship> relationships) {
            throw new UnsupportedOperationException("Paths are not used by these tests");
        }

        @Override
        public Value isoDuration(long months, long days, long seconds, int nanoseconds) {
            throw new UnsupportedOperationException("Durations are not used by these tests");
        }

        @Override
        public Value point(int srid, double x, double y) {
            throw new UnsupportedOperationException("Points are not used by these tests");
        }

        @Override
        public Value point(int srid, double x, double y, double z) {
            throw new UnsupportedOperationException("Points are not used by these tests");
        }

        @Override
        public Value unsupportedDateTimeValue(DateTimeException e) {
            return new SimpleValue(Type.NULL, e);
        }

        private Map<String, Value> toValueMap(Map<?, ?> mapValue) {
            Map<String, Value> values = new LinkedHashMap<>();
            mapValue.forEach((key, rawValue) -> values.put((String) key, value(rawValue)));
            return values;
        }
    }

    private record SimpleValue(Type type, Object rawValue) implements Value {
        @Override
        public boolean asBoolean() {
            return (Boolean) rawValue;
        }

        @Override
        public byte[] asByteArray() {
            return ((byte[]) rawValue).clone();
        }

        @Override
        public String asString() {
            return (String) rawValue;
        }

        @Override
        public long asLong() {
            return (Long) rawValue;
        }

        @Override
        public double asDouble() {
            return (Double) rawValue;
        }

        @Override
        public LocalDate asLocalDate() {
            throw new UnsupportedOperationException("Dates are not used by these tests");
        }

        @Override
        public OffsetTime asOffsetTime() {
            throw new UnsupportedOperationException("Times are not used by these tests");
        }

        @Override
        public LocalTime asLocalTime() {
            throw new UnsupportedOperationException("Times are not used by these tests");
        }

        @Override
        public LocalDateTime asLocalDateTime() {
            throw new UnsupportedOperationException("Date times are not used by these tests");
        }

        @Override
        public ZonedDateTime asZonedDateTime() {
            throw new UnsupportedOperationException("Date times are not used by these tests");
        }

        @Override
        public IsoDuration asIsoDuration() {
            throw new UnsupportedOperationException("Durations are not used by these tests");
        }

        @Override
        public Point asPoint() {
            throw new UnsupportedOperationException("Points are not used by these tests");
        }

        @Override
        public boolean isNull() {
            return type == Type.NULL;
        }

        @Override
        public boolean isEmpty() {
            return size() == 0;
        }

        @Override
        public Iterable<String> keys() {
            return asMapValue().keySet();
        }

        @Override
        public int size() {
            if (type == Type.MAP) {
                return asMapValue().size();
            }
            if (type == Type.LIST) {
                return asListValue().size();
            }
            return 0;
        }

        @Override
        public Value get(String key) {
            return asMapValue().getOrDefault(key, new SimpleValue(Type.NULL, null));
        }

        @Override
        public Iterable<Value> values() {
            if (type == Type.LIST) {
                return asListValue();
            }
            return asMapValue().values();
        }

        @Override
        public boolean containsKey(String key) {
            return asMapValue().containsKey(key);
        }

        @Override
        public <T> Map<String, T> asMap(Function<Value, T> mapFunction) {
            Map<String, T> mapped = new LinkedHashMap<>();
            asMapValue().forEach((key, value) -> mapped.put(key, mapFunction.apply(value)));
            return mapped;
        }

        @SuppressWarnings("unchecked")
        private Map<String, Value> asMapValue() {
            if (type != Type.MAP) {
                return Collections.emptyMap();
            }
            return (Map<String, Value>) rawValue;
        }

        @SuppressWarnings("unchecked")
        private List<Value> asListValue() {
            if (type != Type.LIST) {
                return List.of();
            }
            return (List<Value>) rawValue;
        }
    }
}
