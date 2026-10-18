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
import org.postgresql.PGNotification;
import org.postgresql.ds.PGSimpleDataSource;

import java.io.IOException;
import java.net.ServerSocket;
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
 * Dispatches background jobs through PostgreSQL LISTEN/NOTIFY asynchronous notifications
 * read with `PGConnection.getNotifications`.
 *
 * A dispatcher connection listens on a job channel while a producer enqueues jobs and
 * announces them with `pg_notify` inside a transaction; the dispatcher only sees the
 * announcements once the producer commits, receives them in order with the producer's
 * backend PID, never sees announcements of a rolled-back transaction, and stops receiving
 * a channel after `UNLISTEN`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JobDispatchNotificationTest {

    private static final String POSTGRES_IMAGE = "postgres:18-alpine";

    private static final String DATABASE = "jobs";

    private static final String USER = "dispatcher";

    private static final String PASSWORD = "s3cret";

    private static final String JOB_CHANNEL = "job_created";

    private static final String AUDIT_CHANNEL = "job_audit";

    private final String containerName = "real-use-postgresql-" + UUID.randomUUID();

    private PGSimpleDataSource dataSource;

    @BeforeAll
    void startDatabase() throws Exception {
        int port = freePort();
        Process run = new ProcessBuilder("docker", "run", "-d", "--rm", "--name", containerName,
                "-p", port + ":5432",
                "-e", "POSTGRES_DB=" + DATABASE,
                "-e", "POSTGRES_USER=" + USER,
                "-e", "POSTGRES_PASSWORD=" + PASSWORD,
                POSTGRES_IMAGE)
                .inheritIO()
                .start();
        assertThat(run.waitFor(120, TimeUnit.SECONDS)).isTrue();
        assertThat(run.exitValue()).isZero();

        dataSource = new PGSimpleDataSource();
        dataSource.setServerNames(new String[] {"127.0.0.1"});
        dataSource.setPortNumbers(new int[] {port});
        dataSource.setDatabaseName(DATABASE);
        dataSource.setUser(USER);
        dataSource.setPassword(PASSWORD);
        dataSource.setConnectTimeout(5);
        awaitDatabase();

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE jobs (
                        id      serial PRIMARY KEY,
                        kind    text   NOT NULL,
                        payload text   NOT NULL
                    )""");
        }
    }

    @AfterAll
    void stopDatabase() throws Exception {
        Process remove = new ProcessBuilder("docker", "rm", "-f", containerName).inheritIO().start();
        remove.waitFor(60, TimeUnit.SECONDS);
    }

    @Test
    void dispatcherReceivesCommittedJobAnnouncementsInOrder() throws Exception {
        try (Connection dispatcher = dataSource.getConnection(); Connection producer = dataSource.getConnection()) {
            PGConnection dispatcherChannel = dispatcher.unwrap(PGConnection.class);
            listen(dispatcher, JOB_CHANNEL);
            int producerPid = backendPid(producer);

            producer.setAutoCommit(false);
            int firstJob = enqueue(producer, "resize-image", "photo-17.png");
            int secondJob = enqueue(producer, "send-email", "welcome:ada@example.com");

            PGNotification[] beforeCommit = dispatcherChannel.getNotifications(200);
            assertThat(beforeCommit).isEmpty();

            producer.commit();

            List<PGNotification> received = awaitNotifications(dispatcherChannel, 2);
            assertThat(received).hasSize(2);
            assertThat(received).extracting(PGNotification::getName).containsOnly(JOB_CHANNEL);
            assertThat(received).extracting(PGNotification::getParameter)
                    .containsExactly(firstJob + ":resize-image", secondJob + ":send-email");
            assertThat(received).extracting(PGNotification::getPID).containsOnly(producerPid);

            int announcedJob = Integer.parseInt(received.get(1).getParameter().split(":")[0]);
            try (PreparedStatement lookup = dispatcher.prepareStatement("SELECT kind, payload FROM jobs WHERE id = ?")) {
                lookup.setInt(1, announcedJob);
                try (ResultSet job = lookup.executeQuery()) {
                    assertThat(job.next()).isTrue();
                    assertThat(job.getString("kind")).isEqualTo("send-email");
                    assertThat(job.getString("payload")).isEqualTo("welcome:ada@example.com");
                }
            }
        }
    }

    @Test
    void rolledBackAndUnlistenedAnnouncementsNeverReachDispatcher() throws Exception {
        try (Connection dispatcher = dataSource.getConnection(); Connection producer = dataSource.getConnection()) {
            PGConnection dispatcherChannel = dispatcher.unwrap(PGConnection.class);
            listen(dispatcher, JOB_CHANNEL);
            listen(dispatcher, AUDIT_CHANNEL);

            producer.setAutoCommit(false);
            enqueue(producer, "charge-card", "order-991");
            producer.rollback();

            notify(producer, AUDIT_CHANNEL, "rollback-checked");
            producer.commit();

            List<PGNotification> afterRollback = awaitNotifications(dispatcherChannel, 1);
            assertThat(afterRollback).hasSize(1);
            assertThat(afterRollback.get(0).getName()).isEqualTo(AUDIT_CHANNEL);
            assertThat(afterRollback.get(0).getParameter()).isEqualTo("rollback-checked");

            try (Statement statement = dispatcher.createStatement()) {
                statement.execute("UNLISTEN " + JOB_CHANNEL);
            }
            enqueue(producer, "charge-card", "order-992");
            notify(producer, AUDIT_CHANNEL, "unlisten-checked");
            notify(producer, AUDIT_CHANNEL, "unlisten-checked");
            producer.commit();

            List<PGNotification> afterUnlisten = awaitNotifications(dispatcherChannel, 1);
            assertThat(afterUnlisten).hasSize(1);
            assertThat(afterUnlisten.get(0).getName()).isEqualTo(AUDIT_CHANNEL);
            assertThat(afterUnlisten.get(0).getParameter()).isEqualTo("unlisten-checked");

            try (Statement statement = producer.createStatement();
                    ResultSet count = statement.executeQuery("SELECT count(*) FROM jobs WHERE kind = 'charge-card'")) {
                assertThat(count.next()).isTrue();
                assertThat(count.getInt(1)).isEqualTo(1);
            }
            producer.commit();
        }
    }

    private static void listen(Connection connection, String channel) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("LISTEN " + channel);
        }
    }

    private static void notify(Connection connection, String channel, String payload) throws SQLException {
        try (PreparedStatement notify = connection.prepareStatement("SELECT pg_notify(?, ?)")) {
            notify.setString(1, channel);
            notify.setString(2, payload);
            notify.execute();
        }
    }

    private static int enqueue(Connection connection, String kind, String payload) throws SQLException {
        int id;
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO jobs (kind, payload) VALUES (?, ?) RETURNING id")) {
            insert.setString(1, kind);
            insert.setString(2, payload);
            try (ResultSet generated = insert.executeQuery()) {
                assertThat(generated.next()).isTrue();
                id = generated.getInt(1);
            }
        }
        notify(connection, JOB_CHANNEL, id + ":" + kind);
        return id;
    }

    private static int backendPid(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet pid = statement.executeQuery("SELECT pg_backend_pid()")) {
            assertThat(pid.next()).isTrue();
            return pid.getInt(1);
        }
    }

    /// Polls the listening connection until `expected` notifications arrived, then drains
    /// briefly so that any unexpected extra notification is also returned.
    private static List<PGNotification> awaitNotifications(PGConnection listener, int expected) throws SQLException {
        List<PGNotification> received = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 30_000L;
        while (received.size() < expected && System.currentTimeMillis() < deadline) {
            PGNotification[] batch = listener.getNotifications(500);
            if (batch != null) {
                received.addAll(List.of(batch));
            }
        }
        PGNotification[] trailing = listener.getNotifications(200);
        if (trailing != null) {
            received.addAll(List.of(trailing));
        }
        return received;
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
