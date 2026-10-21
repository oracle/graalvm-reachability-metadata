/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mysql.mysql_connector_j;

import com.mysql.cj.ServerVersion;
import com.mysql.cj.jdbc.JdbcConnection;
import com.mysql.cj.jdbc.MysqlConnectionPoolDataSource;
import com.mysql.cj.jdbc.MysqlDataSource;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.PooledConnection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Application-server connection pooling through `MysqlConnectionPoolDataSource`: a booking
/// service's container-style pool hands out logical handles over `PooledConnection`s, recycles a
/// physical connection when its `ConnectionEventListener` hears a handle close, and discards it
/// when the listener hears a fatal error after the server kills the session.
public class BookingPooledConnectionTest {

    private static final String IMAGE = "container-registry.oracle.com/mysql/community-server:26.7.1";
    private static final String DATABASE = "bookings";
    private static final String USER = "agent";
    private static final String PASSWORD = "agent-secret";
    private static final String CONTAINER = "real-use-mysql-bookings-" + UUID.randomUUID();

    private static MysqlConnectionPoolDataSource poolDataSource;
    private static MysqlDataSource adminDataSource;

    @BeforeAll
    static void startMysql() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        docker("run", "--rm", "-d", "--name", CONTAINER,
                "-e", "MYSQL_DATABASE=" + DATABASE,
                "-e", "MYSQL_USER=" + USER,
                "-e", "MYSQL_PASSWORD=" + PASSWORD,
                "-p", "127.0.0.1:" + port + ":3306",
                IMAGE);
        String url = "jdbc:mysql://127.0.0.1:" + port + "/" + DATABASE;
        poolDataSource = new MysqlConnectionPoolDataSource();
        poolDataSource.setURL(url);
        poolDataSource.setUser(USER);
        poolDataSource.setPassword(PASSWORD);
        adminDataSource = new MysqlDataSource();
        adminDataSource.setURL(url);
        adminDataSource.setUser(USER);
        adminDataSource.setPassword(PASSWORD);
        awaitServer(TimeUnit.MINUTES.toMillis(3));
        createSchema();
    }

    @AfterAll
    static void stopMysql() throws Exception {
        docker("rm", "-f", CONTAINER);
    }

    @Test
    void recyclesClosedHandlesAndDiscardsBrokenConnections() throws SQLException {
        BookingPool pool = new BookingPool(poolDataSource);
        try {
            long firstSession;
            Connection firstHandle = pool.borrow();
            try (Connection connection = firstHandle) {
                firstSession = sessionId(connection);
                book(connection, "Hotel Adriatic", 2);
            }
            assertThat(pool.events()).containsExactly("closed");
            assertThat(pool.idleCount()).isEqualTo(1);

            // A closed logical handle is dead even though its physical connection lives on.
            assertThat(firstHandle.isClosed()).isTrue();
            assertThatThrownBy(firstHandle::createStatement).isInstanceOf(SQLException.class);

            Connection secondHandle = pool.borrow();
            long secondSession = sessionId(secondHandle);
            assertThat(secondSession).isEqualTo(firstSession);
            assertThat(pool.created()).isEqualTo(1);

            // Vendor extensions stay reachable through the logical handle.
            JdbcConnection vendor = secondHandle.unwrap(JdbcConnection.class);
            assertThat(vendor.getServerVersion().meetsMinimum(new ServerVersion(8, 0, 0))).isTrue();
            vendor.ping();

            // The server kills the session behind the handle; the next statement fails and the
            // driver reports the error to the pool before the application sees it.
            killSession(secondSession);
            assertThatThrownBy(() -> book(secondHandle, "Hotel Danube", 1))
                    .isInstanceOfSatisfying(SQLException.class,
                            e -> assertThat(e.getSQLState()).startsWith("08"));
            assertThat(pool.events()).containsExactly("closed", "error");
            secondHandle.close();
            assertThat(pool.events()).containsExactly("closed", "error", "closed");
            assertThat(pool.idleCount()).isZero();
            assertThat(pool.discarded()).isEqualTo(1);

            try (Connection connection = pool.borrow()) {
                assertThat(pool.created()).isEqualTo(2);
                assertThat(sessionId(connection)).isNotEqualTo(firstSession);
                assertThat(bookings(connection)).containsExactly("Hotel Adriatic x2");
            }
            assertThat(pool.idleCount()).isEqualTo(1);
        } finally {
            pool.shutdown();
        }
    }

    private static long sessionId(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT CONNECTION_ID()")) {
            assertThat(row.next()).isTrue();
            return row.getLong(1);
        }
    }

    private static void book(Connection connection, String hotel, int nights) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO booking (hotel, nights) VALUES (?, ?)")) {
            insert.setString(1, hotel);
            insert.setInt(2, nights);
            assertThat(insert.executeUpdate()).isEqualTo(1);
        }
    }

    private static List<String> bookings(Connection connection) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT hotel, nights FROM booking ORDER BY id")) {
            while (result.next()) {
                rows.add(result.getString("hotel") + " x" + result.getInt("nights"));
            }
        }
        return rows;
    }

    /// Ends a session the way an operator would, from a connection outside the pool.
    private static void killSession(long session) throws SQLException {
        try (Connection connection = adminDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("KILL CONNECTION " + session);
        }
    }

    private static void createSchema() throws SQLException {
        try (Connection connection = adminDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE booking (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        hotel VARCHAR(64) NOT NULL,
                        nights INT NOT NULL
                    ) ENGINE = InnoDB
                    """);
        }
    }

    /// Polls with a bounded deadline until the server accepts the application user.
    private static void awaitServer(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        SQLException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection connection = adminDataSource.getConnection();
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

    /// A minimal container-managed pool: idle physical connections wait in a deque, and the
    /// driver's connection events decide whether a released one is reused or thrown away.
    private static final class BookingPool implements ConnectionEventListener {

        private final MysqlConnectionPoolDataSource source;
        private final Deque<PooledConnection> idle = new ArrayDeque<>();
        private final Set<PooledConnection> broken = new HashSet<>();
        private final List<String> events = new ArrayList<>();
        private int created;
        private int discarded;

        BookingPool(MysqlConnectionPoolDataSource source) {
            this.source = source;
        }

        Connection borrow() throws SQLException {
            reap();
            PooledConnection physical = idle.poll();
            if (physical == null) {
                physical = source.getPooledConnection();
                physical.addConnectionEventListener(this);
                created++;
            }
            return physical.getConnection();
        }

        @Override
        public void connectionClosed(ConnectionEvent event) {
            events.add("closed");
            PooledConnection physical = (PooledConnection) event.getSource();
            if (!broken.contains(physical)) {
                idle.push(physical);
            }
        }

        /// Only marks the connection: the driver is still iterating its listeners, so the
        /// physical close waits until the pool next runs `reap`.
        @Override
        public void connectionErrorOccurred(ConnectionEvent event) {
            events.add("error");
            PooledConnection physical = (PooledConnection) event.getSource();
            if (broken.add(physical)) {
                discarded++;
            }
        }

        private void reap() {
            for (PooledConnection physical : broken) {
                try {
                    physical.close();
                } catch (SQLException ignored) {
                    // The session is already gone; closing only releases client resources.
                }
            }
            broken.clear();
        }

        List<String> events() {
            return List.copyOf(events);
        }

        int idleCount() {
            return idle.size();
        }

        int created() {
            return created;
        }

        int discarded() {
            return discarded;
        }

        void shutdown() throws SQLException {
            reap();
            for (PooledConnection physical : idle) {
                physical.close();
            }
            idle.clear();
        }
    }
}
