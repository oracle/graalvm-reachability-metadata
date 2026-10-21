/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_microsoft_sqlserver.mssql_jdbc;

import com.microsoft.sqlserver.jdbc.ISQLServerConnection;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.microsoft.sqlserver.jdbc.SQLServerException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Recovers broken idle connections through the driver's idle connection resiliency:
/// `connectRetryCount` and `connectRetryInterval`, transparent reconnection of a session
/// the server dropped, restoration of the database, language, and date format (but not
/// the session context), and the failures an application sees when recovery is disabled
/// or the session held an open transaction.
///
/// The scenario is a self-service kiosk that keeps one long-lived connection to a real
/// SQL Server while an operator's maintenance job kills idle sessions behind its back.
public class KioskIdleConnectionResiliencyTest {

    private static final String IMAGE = "mcr.microsoft.com/mssql/server:2022-CU14-ubuntu-22.04";

    private static final String PASSWORD = "Kiosk#Retry2024";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(180);

    private static final String CONTAINER = "real-use-mssql-kiosk-" + UUID.randomUUID();

    /// The driver probes the socket for a server-side disconnect only once the connection has
    /// seen no network traffic for 15 seconds; waiting past that fixed threshold makes the
    /// broken session visible to the next statement rather than racing it.
    private static final Duration DRIVER_IDLE_THRESHOLD = Duration.ofSeconds(16);

    private static final Duration KILL_TIMEOUT = Duration.ofSeconds(30);

    private static int port;

    private static SQLServerDataSource admin;

    @BeforeAll
    static void startServer() throws Exception {
        docker("run", "-d", "--name", CONTAINER, "-e", "ACCEPT_EULA=Y", "-e", "MSSQL_SA_PASSWORD=" + PASSWORD,
                "-p", "127.0.0.1::1433", IMAGE);
        String mapping = docker("port", CONTAINER, "1433/tcp").lines().findFirst().orElseThrow();
        port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));

        admin = kioskDataSource("master", 0);
        awaitServer(admin);
        try (Connection connection = admin.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE kiosk");
            statement.execute("CREATE DATABASE kiosk_archive");
            statement.execute("""
                    CREATE TABLE kiosk.dbo.orders (
                        id INT IDENTITY PRIMARY KEY,
                        terminal NVARCHAR(10) NOT NULL,
                        item NVARCHAR(40) NOT NULL
                    )""");
            statement.execute("CREATE TABLE kiosk_archive.dbo.receipts (id INT PRIMARY KEY, total DECIMAL(8, 2))");
            statement.execute("INSERT INTO kiosk_archive.dbo.receipts VALUES (1, 12.50), (2, 7.25)");
        }
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @Test
    void droppedIdleSessionIsReconnectedWithItsState() throws Exception {
        try (Connection connection = kioskDataSource("kiosk", 3).getConnection()) {
            ISQLServerConnection driverConnection = connection.unwrap(ISQLServerConnection.class);
            UUID originalConnectionId = driverConnection.getClientConnectionId();
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO orders (terminal, item) VALUES (N'T-07', N'Flat white')");
                statement.execute("USE kiosk_archive");
                statement.execute("SET LANGUAGE Deutsch");
                statement.execute("SET DATEFORMAT ymd");
                statement.execute("EXEC sp_set_session_context N'terminal', N'T-07'");
            }
            SessionState before = sessionState(connection);
            assertThat(before.database()).isEqualTo("kiosk_archive");
            assertThat(before.language()).isEqualTo("Deutsch");
            assertThat(before.dateFormat()).isEqualTo("ymd");
            assertThat(before.terminal()).isEqualTo("T-07");
            Instant lastUse = Instant.now();

            killSession(before.spid());
            awaitIdleWindow(lastUse);

            SessionState after = sessionState(connection);
            assertThat(after.serverConnectionId()).isNotEqualTo(before.serverConnectionId());
            assertThat(after.database()).isEqualTo("kiosk_archive");
            assertThat(after.language()).isEqualTo("Deutsch");
            assertThat(after.dateFormat()).isEqualTo("ymd");
            assertThat(after.terminal()).isNull();
            assertThat(connection.isClosed()).isFalse();
            assertThat(driverConnection.getClientConnectionId()).isNotNull().isNotEqualTo(originalConnectionId);

            try (Statement statement = connection.createStatement()) {
                statement.execute("EXEC sp_set_session_context N'terminal', N'T-07'");
            }
            assertThat(sessionState(connection).terminal()).isEqualTo("T-07");

            try (PreparedStatement receipts = connection.prepareStatement(
                    "SELECT COUNT(*), SUM(total) FROM receipts WHERE id >= ?")) {
                receipts.setInt(1, 1);
                try (ResultSet resultSet = receipts.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getInt(1)).isEqualTo(2);
                    assertThat(resultSet.getBigDecimal(2)).isEqualByComparingTo("19.75");
                }
            }
            assertThat(ordersFor("T-07")).containsExactly("Flat white");
        }
    }

    @Test
    void connectionWithoutRetriesReportsTheBrokenLink() throws Exception {
        try (Connection connection = kioskDataSource("kiosk", 0).getConnection()) {
            int spid = sessionState(connection).spid();
            Instant lastUse = Instant.now();

            killSession(spid);
            awaitIdleWindow(lastUse);

            assertThatThrownBy(() -> sessionState(connection)).isInstanceOf(SQLServerException.class);
            assertThat(connection.isClosed()).isTrue();
            assertThat(connection.isValid(5)).isFalse();
        }
    }

    @Test
    void sessionWithAnOpenTransactionIsNotRecovered() throws Exception {
        try (Connection connection = kioskDataSource("kiosk", 3).getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO orders (terminal, item) VALUES (?, ?)")) {
                insert.setString(1, "T-12");
                insert.setString(2, "Iced tea");
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
            int spid = sessionState(connection).spid();
            Instant lastUse = Instant.now();

            killSession(spid);
            awaitIdleWindow(lastUse);

            assertThatThrownBy(() -> sessionState(connection))
                    .isInstanceOf(SQLServerException.class)
                    .hasMessageContaining("recovery is not possible");
        }
        assertThat(ordersFor("T-12")).isEmpty();
    }

    @Test
    void retrySettingsOutsideTheSupportedRangeAreRejected() {
        SQLServerDataSource tooManyRetries = kioskDataSource("kiosk", 256);
        assertThatThrownBy(tooManyRetries::getConnection)
                .isInstanceOf(SQLServerException.class)
                .hasMessageContaining("Connection retry count");

        SQLServerDataSource tooLongInterval = kioskDataSource("kiosk", 1);
        tooLongInterval.setConnectRetryInterval(61);
        assertThatThrownBy(tooLongInterval::getConnection)
                .isInstanceOf(SQLServerException.class)
                .hasMessageContaining("Connection retry interval");
    }

    /// The session attributes the kiosk relies on, as the server currently reports them.
    record SessionState(int spid, String serverConnectionId, String database, String language,
            String dateFormat, String terminal) {
    }

    private static SQLServerDataSource kioskDataSource(String database, int connectRetryCount) {
        SQLServerDataSource dataSource = new SQLServerDataSource();
        dataSource.setServerName("127.0.0.1");
        dataSource.setPortNumber(port);
        dataSource.setDatabaseName(database);
        dataSource.setUser("sa");
        dataSource.setPassword(PASSWORD);
        dataSource.setEncrypt("false");
        dataSource.setLoginTimeout(10);
        dataSource.setApplicationName("kiosk");
        dataSource.setConnectRetryCount(connectRetryCount);
        dataSource.setConnectRetryInterval(1);
        return dataSource;
    }

    private static SessionState sessionState(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT @@SPID, CAST(c.connection_id AS NVARCHAR(36)), DB_NAME(), @@LANGUAGE,
                            s.date_format, CAST(SESSION_CONTEXT(N'terminal') AS NVARCHAR(10))
                     FROM sys.dm_exec_sessions s
                     JOIN sys.dm_exec_connections c ON c.session_id = s.session_id
                     WHERE s.session_id = @@SPID""")) {
            assertThat(resultSet.next()).isTrue();
            return new SessionState(resultSet.getInt(1), resultSet.getString(2), resultSet.getString(3),
                    resultSet.getString(4), resultSet.getString(5), resultSet.getString(6));
        }
    }

    private static List<String> ordersFor(String terminal) throws SQLException {
        List<String> items = new ArrayList<>();
        try (Connection connection = admin.getConnection();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT item FROM kiosk.dbo.orders WHERE terminal = ? ORDER BY id")) {
            query.setString(1, terminal);
            try (ResultSet resultSet = query.executeQuery()) {
                while (resultSet.next()) {
                    items.add(resultSet.getString(1));
                }
            }
        }
        return items;
    }

    /// Kills the session from the operator's connection and waits until the server has
    /// removed it, so the kiosk's socket is closed before the kiosk uses it again.
    private static void killSession(int spid) throws Exception {
        try (Connection connection = admin.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("KILL " + spid);
            long deadline = System.nanoTime() + KILL_TIMEOUT.toNanos();
            while (System.nanoTime() < deadline) {
                try (ResultSet resultSet = statement.executeQuery(
                        "SELECT COUNT(*) FROM sys.dm_exec_sessions WHERE session_id = " + spid)) {
                    assertThat(resultSet.next()).isTrue();
                    if (resultSet.getInt(1) == 0) {
                        return;
                    }
                }
                Thread.sleep(200);
            }
        }
        throw new IllegalStateException("Session " + spid + " was not removed in time");
    }

    private static void awaitIdleWindow(Instant lastUse) throws InterruptedException {
        Duration remaining = Duration.between(Instant.now(), lastUse.plus(DRIVER_IDLE_THRESHOLD));
        if (!remaining.isNegative()) {
            Thread.sleep(remaining.toMillis() + 1);
        }
    }

    private static void awaitServer(SQLServerDataSource dataSource) throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        SQLException last = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
                return;
            } catch (SQLException e) {
                last = e;
            }
            Thread.sleep(1_000);
        }
        throw new IllegalStateException("SQL Server did not accept connections in time", last);
    }

    private static String docker(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (InputStream stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        return output;
    }
}
