/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_postgresql.postgresql;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.postgresql.PGConnection;
import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.jdbc.PreferQueryMode;
import org.postgresql.replication.LogSequenceNumber;
import org.postgresql.replication.PGReplicationStream;
import org.postgresql.replication.ReplicationSlotInfo;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Captures order table changes through PostgreSQL logical replication streamed by the driver's
 * replication API (`PGReplicationConnection` and `PGReplicationStream`).
 *
 * A change-data-capture consumer creates a `test_decoding` logical slot, streams the decoded
 * inserts, updates and deletes of committed order transactions while rolled-back work never
 * appears, acknowledges what it processed with flush feedback, and resumes from the slot on a
 * new connection receiving only the changes committed after its acknowledgement.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderChangeCaptureReplicationTest {

    private static final String POSTGRES_IMAGE = "postgres:18-alpine";

    private static final String DATABASE = "shop";

    private static final String USER = "capture";

    private static final String PASSWORD = "s3cret";

    private final String containerName = "real-use-postgresql-" + UUID.randomUUID();

    private PGSimpleDataSource dataSource;

    private PGSimpleDataSource replicationSource;

    @BeforeAll
    void startDatabase() throws Exception {
        int port = freePort();
        Process run = new ProcessBuilder("docker", "run", "-d", "--rm", "--name", containerName,
                "-p", port + ":5432",
                "-e", "POSTGRES_DB=" + DATABASE,
                "-e", "POSTGRES_USER=" + USER,
                "-e", "POSTGRES_PASSWORD=" + PASSWORD,
                POSTGRES_IMAGE,
                "-c", "wal_level=logical")
                .inheritIO()
                .start();
        assertThat(run.waitFor(120, TimeUnit.SECONDS)).isTrue();
        assertThat(run.exitValue()).isZero();

        dataSource = dataSource(port);
        replicationSource = dataSource(port);
        replicationSource.setReplication("database");
        replicationSource.setAssumeMinServerVersion("10");
        replicationSource.setPreferQueryMode(PreferQueryMode.SIMPLE);
        awaitDatabase();

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE orders (
                        id       integer PRIMARY KEY,
                        customer text    NOT NULL,
                        status   text    NOT NULL
                    )""");
        }
    }

    @AfterAll
    void stopDatabase() throws Exception {
        Process remove = new ProcessBuilder("docker", "rm", "-f", containerName).inheritIO().start();
        remove.waitFor(60, TimeUnit.SECONDS);
    }

    @Test
    void consumerStreamsDecodedChangesOfCommittedOrderTransactions() throws Exception {
        String slot = "orders_cdc_stream";
        try (Connection replication = replicationSource.getConnection(); Connection shop = dataSource.getConnection()) {
            PGConnection replicationConnection = replication.unwrap(PGConnection.class);
            ReplicationSlotInfo slotInfo = replicationConnection.getReplicationAPI()
                    .createReplicationSlot()
                    .logical()
                    .withSlotName(slot)
                    .withOutputPlugin("test_decoding")
                    .make();
            assertThat(slotInfo.getSlotName()).isEqualTo(slot);
            assertThat(slotInfo.getOutputPlugin()).isEqualTo("test_decoding");
            assertThat(slotInfo.getConsistentPoint()).isNotEqualTo(LogSequenceNumber.INVALID_LSN);

            PGReplicationStream stream = openStream(replicationConnection, slot);

            shop.setAutoCommit(false);
            try (Statement statement = shop.createStatement()) {
                statement.executeUpdate("INSERT INTO orders VALUES (1, 'ada', 'new'), (2, 'grace', 'new')");
                statement.executeUpdate("UPDATE orders SET status = 'paid' WHERE id = 1");
                statement.executeUpdate("DELETE FROM orders WHERE id = 2");
            }
            shop.commit();
            try (Statement statement = shop.createStatement()) {
                statement.executeUpdate("INSERT INTO orders VALUES (3, 'linus', 'new')");
            }
            shop.rollback();
            try (Statement statement = shop.createStatement()) {
                statement.executeUpdate("UPDATE orders SET status = 'shipped' WHERE id = 1");
            }
            shop.commit();

            List<String> changes = readTransactions(stream, 2);
            assertThat(changes).containsExactly(
                    "BEGIN",
                    "table public.orders: INSERT: id[integer]:1 customer[text]:'ada' status[text]:'new'",
                    "table public.orders: INSERT: id[integer]:2 customer[text]:'grace' status[text]:'new'",
                    "table public.orders: UPDATE: id[integer]:1 customer[text]:'ada' status[text]:'paid'",
                    "table public.orders: DELETE: id[integer]:2",
                    "COMMIT",
                    "BEGIN",
                    "table public.orders: UPDATE: id[integer]:1 customer[text]:'ada' status[text]:'shipped'",
                    "COMMIT");
            assertThat(changes).noneMatch(change -> change.contains("linus"));

            stream.close();
            replicationConnection.getReplicationAPI().dropReplicationSlot(slot);
            assertThat(slotExists(shop, slot)).isFalse();
            shop.commit();
        }
    }

    @Test
    void consumerResumesAfterAcknowledgedChangesOnly() throws Exception {
        String slot = "orders_cdc_resume";
        try (Connection shop = dataSource.getConnection()) {
            try (Connection replication = replicationSource.getConnection()) {
                PGConnection replicationConnection = replication.unwrap(PGConnection.class);
                replicationConnection.getReplicationAPI()
                        .createReplicationSlot()
                        .logical()
                        .withSlotName(slot)
                        .withOutputPlugin("test_decoding")
                        .make();
                PGReplicationStream stream = openStream(replicationConnection, slot);

                execute(shop, "INSERT INTO orders VALUES (10, 'margaret', 'new')");
                List<String> firstBatch = readTransactions(stream, 1);
                assertThat(firstBatch).containsExactly(
                        "BEGIN",
                        "table public.orders: INSERT: id[integer]:10 customer[text]:'margaret' status[text]:'new'",
                        "COMMIT");

                LogSequenceNumber processed = stream.getLastReceiveLSN();
                stream.setAppliedLSN(processed);
                stream.setFlushedLSN(processed);
                stream.forceUpdateStatus();
                awaitConfirmedFlush(shop, slot, processed);
                stream.close();
            }

            awaitSlotReleased(shop, slot);
            execute(shop, "UPDATE orders SET status = 'packed' WHERE id = 10");

            try (Connection replication = replicationSource.getConnection()) {
                PGConnection replicationConnection = replication.unwrap(PGConnection.class);
                PGReplicationStream stream = openStream(replicationConnection, slot);
                List<String> resumed = readTransactions(stream, 1);
                assertThat(resumed).containsExactly(
                        "BEGIN",
                        "table public.orders: UPDATE: id[integer]:10 customer[text]:'margaret' status[text]:'packed'",
                        "COMMIT");
                stream.close();
                replicationConnection.getReplicationAPI().dropReplicationSlot(slot);
            }
            assertThat(slotExists(shop, slot)).isFalse();
        }
    }

    private static PGReplicationStream openStream(PGConnection replicationConnection, String slot) throws SQLException {
        return replicationConnection.getReplicationAPI()
                .replicationStream()
                .logical()
                .withSlotName(slot)
                .withSlotOption("include-xids", false)
                .withSlotOption("skip-empty-xacts", true)
                .withStatusInterval(10, TimeUnit.SECONDS)
                .start();
    }

    /// Polls the stream without blocking until `transactions` decoded transactions arrived.
    private static List<String> readTransactions(PGReplicationStream stream, int transactions) throws Exception {
        List<String> changes = new ArrayList<>();
        int commits = 0;
        long deadline = System.currentTimeMillis() + 30_000L;
        while (commits < transactions && System.currentTimeMillis() < deadline) {
            ByteBuffer message = stream.readPending();
            if (message == null) {
                Thread.sleep(10L);
                continue;
            }
            int offset = message.arrayOffset();
            byte[] source = message.array();
            String change = new String(source, offset, source.length - offset, StandardCharsets.UTF_8);
            changes.add(change);
            if (change.equals("COMMIT")) {
                commits++;
            }
        }
        return changes;
    }

    private static void awaitConfirmedFlush(Connection shop, String slot, LogSequenceNumber processed) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000L;
        LogSequenceNumber confirmed = LogSequenceNumber.INVALID_LSN;
        try (PreparedStatement query = shop.prepareStatement(
                "SELECT confirmed_flush_lsn FROM pg_replication_slots WHERE slot_name = ?")) {
            query.setString(1, slot);
            while (System.currentTimeMillis() < deadline) {
                try (ResultSet row = query.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    confirmed = LogSequenceNumber.valueOf(row.getString(1));
                }
                if (Long.compareUnsigned(confirmed.asLong(), processed.asLong()) >= 0) {
                    return;
                }
                Thread.sleep(50L);
            }
        }
        throw new IllegalStateException("Slot " + slot + " confirmed " + confirmed.asString()
                + " but the consumer acknowledged " + processed.asString());
    }

    /// Waits until the walsender of the closed consumer connection released the slot.
    private static void awaitSlotReleased(Connection shop, String slot) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000L;
        try (PreparedStatement query = shop.prepareStatement(
                "SELECT active FROM pg_replication_slots WHERE slot_name = ?")) {
            query.setString(1, slot);
            while (System.currentTimeMillis() < deadline) {
                try (ResultSet row = query.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    if (!row.getBoolean(1)) {
                        return;
                    }
                }
                Thread.sleep(50L);
            }
        }
        throw new IllegalStateException("Slot " + slot + " is still held by a closed consumer");
    }

    private static boolean slotExists(Connection shop, String slot) throws SQLException {
        try (PreparedStatement query = shop.prepareStatement(
                "SELECT count(*) FROM pg_replication_slots WHERE slot_name = ?")) {
            query.setString(1, slot);
            try (ResultSet row = query.executeQuery()) {
                assertThat(row.next()).isTrue();
                return row.getInt(1) == 1;
            }
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static PGSimpleDataSource dataSource(int port) {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setServerNames(new String[] {"127.0.0.1"});
        source.setPortNumbers(new int[] {port});
        source.setDatabaseName(DATABASE);
        source.setUser(USER);
        source.setPassword(PASSWORD);
        source.setConnectTimeout(5);
        return source;
    }

    private void awaitDatabase() throws Exception {
        long deadline = System.currentTimeMillis() + 120_000L;
        SQLException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection probe = dataSource.getConnection(); Statement statement = probe.createStatement()) {
                statement.execute("SELECT 1");
                return;
            } catch (SQLException e) {
                lastFailure = e;
                Thread.sleep(500L);
            }
        }
        throw new IllegalStateException("PostgreSQL did not accept connections in " + containerName, lastFailure);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
