/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mysql.mysql_connector_j;

import com.mysql.cj.jdbc.MysqlDataSource;
import com.mysql.cj.jdbc.exceptions.MySQLTimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Statement execution control and connection liveness: a reporting service whose watchdog
/// bounds runaway queries with `setQueryTimeout` (a server-side `KILL QUERY` that leaves the
/// session usable), caps report sizes with `setMaxRows`/`setLargeMaxRows`, notices sessions the
/// server killed or whose socket read timed out through link failures and `isValid`, and
/// releases a session with `Connection.abort`.
public class ReportingQueryWatchdogTest {

    private static final String IMAGE = "container-registry.oracle.com/mysql/community-server:26.7.1";
    private static final String DATABASE = "reporting";
    private static final String USER = "reporter";
    private static final String PASSWORD = "reporter-secret";
    private static final String CONTAINER = "real-use-mysql-reporting-" + UUID.randomUUID();
    private static final String RUNAWAY_REPORT = "SELECT SLEEP(60) AS runaway_report";

    private static int port;

    @BeforeAll
    static void startMysql() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        docker("run", "--rm", "-d", "--name", CONTAINER,
                "-e", "MYSQL_RANDOM_ROOT_PASSWORD=yes",
                "-e", "MYSQL_DATABASE=" + DATABASE,
                "-e", "MYSQL_USER=" + USER,
                "-e", "MYSQL_PASSWORD=" + PASSWORD,
                "-p", "127.0.0.1:" + port + ":3306",
                IMAGE);
        awaitServer(reporter(), TimeUnit.MINUTES.toMillis(3));
        try (Connection connection = reporter().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sale (id INT PRIMARY KEY, region VARCHAR(8) NOT NULL, amount INT NOT NULL)");
            statement.executeUpdate("""
                    INSERT INTO sale VALUES
                        (1, 'north', 120), (2, 'south', 80), (3, 'north', 45),
                        (4, 'east', 300), (5, 'south', 15), (6, 'west', 60)
                    """);
        }
    }

    @AfterAll
    static void stopMysql() throws Exception {
        docker("rm", "-f", CONTAINER);
    }

    @Test
    void queryTimeoutKillsOnlyTheRunawayQuery() throws Exception {
        try (Connection connection = reporter().getConnection();
             Statement statement = connection.createStatement()) {
            long sessionId = sessionId(connection);
            statement.setQueryTimeout(1);
            assertThat(statement.getQueryTimeout()).isEqualTo(1);

            assertThatThrownBy(() -> statement.executeQuery(RUNAWAY_REPORT))
                    .isInstanceOf(MySQLTimeoutException.class)
                    .hasMessageContaining("Statement cancelled due to timeout");

            // Only the statement was killed: the same session keeps serving reports.
            assertThat(connection.isValid(5)).isTrue();
            assertThat(sessionId(connection)).isEqualTo(sessionId);
            assertThat(regionTotal(connection, "north")).isEqualTo(165);
        }
    }

    @Test
    void detectsSessionsLostToTheServerOrTheNetwork() throws Exception {
        // An administrator kills the session; the next statement fails with a link failure.
        try (Connection victim = reporter().getConnection()) {
            long sessionId = sessionId(victim);
            try (Connection admin = reporter().getConnection();
                 Statement statement = admin.createStatement()) {
                statement.execute("KILL CONNECTION " + sessionId);
            }
            awaitGone(sessionId, TimeUnit.SECONDS.toMillis(30));

            assertThatThrownBy(() -> regionTotal(victim, "east"))
                    .isInstanceOfSatisfying(SQLException.class,
                            e -> assertThat(e.getSQLState()).startsWith("08"));
            assertThat(victim.isValid(5)).isFalse();
            assertThat(victim.isClosed()).isTrue();
        }

        // A socket read timeout leaves the protocol out of step, so the driver drops the session.
        MysqlDataSource impatient = reporter();
        impatient.setSocketTimeout(1000);
        try (Connection connection = impatient.getConnection()) {
            assertThat(regionTotal(connection, "east")).isEqualTo(300);

            assertThatThrownBy(() -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeQuery(RUNAWAY_REPORT);
                }
            }).isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("08S01"));
            assertThat(connection.isClosed()).isTrue();
            assertThat(connection.isValid(5)).isFalse();
        }
    }

    @Test
    void maxRowsCapsReportsWithoutChangingTheQuery() throws Exception {
        try (Connection connection = reporter().getConnection();
             Statement statement = connection.createStatement()) {
            statement.setMaxRows(2);
            assertThat(statement.getMaxRows()).isEqualTo(2);
            assertThat(topSales(statement)).containsExactly(300, 120);

            statement.setLargeMaxRows(4L);
            assertThat(statement.getLargeMaxRows()).isEqualTo(4L);
            assertThat(topSales(statement)).containsExactly(300, 120, 80, 60);

            statement.setMaxRows(0);
            assertThat(topSales(statement)).containsExactly(300, 120, 80, 60, 45, 15);
        }
    }

    @Test
    void abortReleasesTheServerSession() throws Exception {
        ExecutorService aborts = Executors.newSingleThreadExecutor();
        try {
            Connection connection = reporter().getConnection();
            long sessionId = sessionId(connection);
            assertThat(regionTotal(connection, "west")).isEqualTo(60);

            connection.abort(aborts);
            // The executor finishes closing the session, so wait for it before observing the result.
            aborts.shutdown();
            assertThat(aborts.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

            assertThat(connection.isClosed()).isTrue();
            assertThatThrownBy(connection::createStatement)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("No operations allowed after connection closed");
            awaitGone(sessionId, TimeUnit.SECONDS.toMillis(30));
        } finally {
            aborts.shutdown();
            assertThat(aborts.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static MysqlDataSource reporter() {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL("jdbc:mysql://127.0.0.1:" + port + "/" + DATABASE);
        dataSource.setUser(USER);
        dataSource.setPassword(PASSWORD);
        return dataSource;
    }

    private static long sessionId(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT CONNECTION_ID()")) {
            assertThat(rows.next()).isTrue();
            return rows.getLong(1);
        }
    }

    private static int regionTotal(Connection connection, String region) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT SUM(amount) FROM sale WHERE region = ?")) {
            statement.setString(1, region);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getInt(1);
            }
        }
    }

    private static List<Integer> topSales(Statement statement) throws SQLException {
        List<Integer> amounts = new ArrayList<>();
        try (ResultSet rows = statement.executeQuery("SELECT amount FROM sale ORDER BY amount DESC")) {
            while (rows.next()) {
                amounts.add(rows.getInt(1));
            }
        }
        return amounts;
    }

    /// Returns the statement a session is executing, or `null` once the session no longer exists.
    private static String runningStatement(long sessionId) throws SQLException {
        try (Connection monitor = reporter().getConnection();
             PreparedStatement statement = monitor.prepareStatement(
                     "SELECT COALESCE(INFO, '') FROM performance_schema.processlist WHERE ID = ?")) {
            statement.setLong(1, sessionId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }

    /// Polls with a bounded deadline until the server has discarded the session.
    private static void awaitGone(long sessionId, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (runningStatement(sessionId) != null) {
            assertThat(System.currentTimeMillis()).as("session %d ended", sessionId).isLessThan(deadline);
            Thread.sleep(100);
        }
    }

    /// Polls with a bounded deadline until the server accepts the application user.
    private static void awaitServer(MysqlDataSource dataSource, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        SQLException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
                return;
            } catch (SQLException e) {
                lastFailure = e;
                Thread.sleep(500);
            }
        }
        throw new IllegalStateException("MySQL did not become reachable", lastFailure);
    }

    private static void docker(String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).inheritIO().start();
        assertThat(process.waitFor(5, TimeUnit.MINUTES)).as("docker %s finished", args[0]).isTrue();
        assertThat(process.exitValue()).as("docker %s exit code", args[0]).isZero();
    }
}
