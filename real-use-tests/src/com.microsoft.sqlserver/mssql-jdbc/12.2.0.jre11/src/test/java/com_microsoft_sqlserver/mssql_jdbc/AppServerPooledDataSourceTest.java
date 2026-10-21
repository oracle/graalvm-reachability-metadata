/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_microsoft_sqlserver.mssql_jdbc;

import com.microsoft.sqlserver.jdbc.ISQLServerConnection;
import com.microsoft.sqlserver.jdbc.SQLServerConnectionPoolDataSource;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.microsoft.sqlserver.jdbc.SQLServerDataSourceObjectFactory;
import com.microsoft.sqlserver.jdbc.SQLServerException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.PooledConnection;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Exercises the driver's `javax.sql` application-server plumbing: physical sessions
/// from `SQLServerConnectionPoolDataSource` as `PooledConnection`s with logical handles,
/// `ConnectionEventListener` close and fatal-error events, session reset between handles,
/// and data sources bound as JNDI `Reference`s or serialized and rebuilt without their
/// password, which the application supplies again after the lookup.
///
/// The scenario is a small application server that pools sessions for a ticketing app,
/// publishes its data sources in a naming directory, and replicates them to a peer node.
public class AppServerPooledDataSourceTest {

    private static final String IMAGE = "mcr.microsoft.com/mssql/server:2022-CU14-ubuntu-22.04";

    private static final String PASSWORD = "Tickets#Pool2024";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(180);

    private static final Duration KILL_TIMEOUT = Duration.ofSeconds(30);

    private static final String CONTAINER = "real-use-mssql-appserver-" + UUID.randomUUID();

    private static int port;

    private static SQLServerDataSource admin;

    @BeforeAll
    static void startServer() throws Exception {
        docker("run", "-d", "--name", CONTAINER, "-e", "ACCEPT_EULA=Y", "-e", "MSSQL_SA_PASSWORD=" + PASSWORD,
                "-p", "127.0.0.1::1433", IMAGE);
        String mapping = docker("port", CONTAINER, "1433/tcp").lines().findFirst().orElseThrow();
        port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));

        admin = configure(new SQLServerDataSource(), "master");
        awaitServer(admin);
        try (Connection connection = admin.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE tickets");
            statement.execute("""
                    CREATE TABLE tickets.dbo.seats (
                        seat NVARCHAR(4) PRIMARY KEY,
                        holder NVARCHAR(40) NULL
                    )""");
            statement.execute("INSERT INTO tickets.dbo.seats VALUES (N'A1', NULL), (N'A2', N'Ines'), (N'A3', NULL)");
        }
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @Test
    void logicalHandlesShareOneResetPhysicalSession() throws Exception {
        SQLServerConnectionPoolDataSource poolSource = configure(new SQLServerConnectionPoolDataSource(), "tickets");
        PooledConnection pooled = poolSource.getPooledConnection();
        RecordingListener listener = new RecordingListener();
        pooled.addConnectionEventListener(listener);
        try {
            Connection first = pooled.getConnection();
            UUID clientConnectionId = first.unwrap(ISQLServerConnection.class).getClientConnectionId();
            int spid = spid(first);
            try (Statement statement = first.createStatement()) {
                statement.execute("CREATE TABLE #cart (seat NVARCHAR(4))");
                statement.execute("INSERT INTO #cart VALUES (N'A1')");
                assertThat(singleInt(statement, "SELECT COUNT(*) FROM #cart")).isEqualTo(1);
            }
            first.close();

            assertThat(first.isClosed()).isTrue();
            assertThatThrownBy(first::createStatement).isInstanceOf(SQLServerException.class);
            assertThat(listener.closed).hasSize(1);
            assertThat(listener.closed.get(0).getSource()).isSameAs(pooled);
            assertThat(listener.errors).isEmpty();

            try (Connection second = pooled.getConnection(); Statement statement = second.createStatement()) {
                assertThat(spid(second)).isEqualTo(spid);
                assertThat(second.unwrap(ISQLServerConnection.class).getClientConnectionId())
                        .isEqualTo(clientConnectionId);
                assertThat(singleInt(statement, "SELECT CASE WHEN OBJECT_ID('tempdb..#cart') IS NULL THEN 0 ELSE 1 END"))
                        .isZero();
                assertThat(singleInt(statement, "SELECT COUNT(*) FROM seats WHERE holder IS NULL")).isEqualTo(2);
            }
            assertThat(listener.closed).hasSize(2);
        } finally {
            pooled.close();
        }
    }

    @Test
    void openingAnotherHandleRetiresTheOutstandingOne() throws Exception {
        PooledConnection pooled = configure(new SQLServerConnectionPoolDataSource(), "tickets").getPooledConnection();
        try {
            Connection stale = pooled.getConnection();
            try (Connection fresh = pooled.getConnection(); Statement statement = fresh.createStatement()) {
                assertThat(stale.isClosed()).isTrue();
                assertThatThrownBy(stale::createStatement).isInstanceOf(SQLServerException.class);
                assertThat(singleInt(statement, "SELECT COUNT(*) FROM seats")).isEqualTo(3);
            }
        } finally {
            pooled.close();
        }
    }

    @Test
    void killedSessionIsReportedAsAConnectionError() throws Exception {
        SQLServerConnectionPoolDataSource poolSource = configure(new SQLServerConnectionPoolDataSource(), "tickets");
        poolSource.setConnectRetryCount(0);
        PooledConnection pooled = poolSource.getPooledConnection();
        RecordingListener listener = new RecordingListener();
        pooled.addConnectionEventListener(listener);
        try {
            Connection handle = pooled.getConnection();
            killSession(spid(handle));

            assertThatThrownBy(() -> spid(handle)).isInstanceOf(SQLServerException.class);
            assertThat(listener.errors).isNotEmpty().allSatisfy(event -> {
                assertThat(event.getSource()).isSameAs(pooled);
                Throwable reported = event.getSQLException();
                assertThat(reported).isInstanceOf(SQLServerException.class);
            });
            assertThat(listener.closed).isEmpty();
            assertThat(handle.isClosed()).isTrue();
        } finally {
            pooled.close();
        }
    }

    @Test
    void dataSourceIsRebuiltFromItsNamingReference() throws Exception {
        SQLServerDataSource published = configure(new SQLServerDataSource(), "tickets");
        published.setApplicationName("box-office");

        Reference reference = published.getReference();
        assertThat(reference.getFactoryClassName()).isEqualTo(SQLServerDataSourceObjectFactory.class.getName());
        assertThat(reference.getClassName()).isEqualTo(SQLServerDataSource.class.getName());

        Object looked = new SQLServerDataSourceObjectFactory().getObjectInstance(reference, null, null, null);
        assertThat(looked).isInstanceOf(SQLServerDataSource.class);
        SQLServerDataSource rebuilt = (SQLServerDataSource) looked;
        assertThat(rebuilt.getDatabaseName()).isEqualTo("tickets");
        assertThat(rebuilt.getPortNumber()).isEqualTo(port);
        assertThat(rebuilt.getApplicationName()).isEqualTo("box-office");
        assertThatThrownBy(() -> applicationAndDatabase(rebuilt))
                .isInstanceOf(SQLServerException.class)
                .hasMessageContaining("Login failed");
        rebuilt.setPassword(PASSWORD);
        assertThat(applicationAndDatabase(rebuilt)).containsExactly("box-office", "tickets");

        Reference poolReference = configure(new SQLServerConnectionPoolDataSource(), "tickets").getReference();
        Object pool = new SQLServerDataSourceObjectFactory().getObjectInstance(poolReference, null, null, null);
        assertThat(pool).isInstanceOf(SQLServerConnectionPoolDataSource.class);
        SQLServerConnectionPoolDataSource rebuiltPool = (SQLServerConnectionPoolDataSource) pool;
        rebuiltPool.setPassword(PASSWORD);
        PooledConnection pooled = rebuiltPool.getPooledConnection();
        try (Connection connection = pooled.getConnection(); Statement statement = connection.createStatement()) {
            assertThat(singleInt(statement, "SELECT COUNT(*) FROM seats WHERE holder = N'Ines'")).isEqualTo(1);
        } finally {
            pooled.close();
        }

        Reference foreign = new Reference(SQLServerDataSource.class.getName(),
                new StringRefAddr("class", "com.example.NotADataSource"),
                SQLServerDataSourceObjectFactory.class.getName(), null);
        assertThatThrownBy(() -> new SQLServerDataSourceObjectFactory().getObjectInstance(foreign, null, null, null))
                .isInstanceOf(SQLServerException.class);
    }

    @Test
    void serializedDataSourceConnectsOnThePeerNode() throws Exception {
        SQLServerDataSource original = configure(new SQLServerDataSource(), "tickets");
        original.setApplicationName("replica");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }
        Object restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = input.readObject();
        }

        assertThat(restored).isInstanceOf(SQLServerDataSource.class).isNotSameAs(original);
        SQLServerDataSource copy = (SQLServerDataSource) restored;
        assertThat(copy.getServerName()).isEqualTo("127.0.0.1");
        assertThat(copy.getApplicationName()).isEqualTo("replica");
        assertThatThrownBy(() -> applicationAndDatabase(copy))
                .isInstanceOf(SQLServerException.class)
                .hasMessageContaining("Login failed");
        copy.setPassword(PASSWORD);
        assertThat(applicationAndDatabase(copy)).containsExactly("replica", "tickets");
    }

    /// Records the events the pool manager receives for one physical connection.
    static final class RecordingListener implements ConnectionEventListener {

        final List<ConnectionEvent> closed = new CopyOnWriteArrayList<>();

        final List<ConnectionEvent> errors = new CopyOnWriteArrayList<>();

        @Override
        public void connectionClosed(ConnectionEvent event) {
            closed.add(event);
        }

        @Override
        public void connectionErrorOccurred(ConnectionEvent event) {
            errors.add(event);
        }
    }

    private static <T extends SQLServerDataSource> T configure(T dataSource, String database) {
        dataSource.setServerName("127.0.0.1");
        dataSource.setPortNumber(port);
        dataSource.setDatabaseName(database);
        dataSource.setUser("sa");
        dataSource.setPassword(PASSWORD);
        dataSource.setEncrypt("false");
        dataSource.setLoginTimeout(10);
        return dataSource;
    }

    private static List<String> applicationAndDatabase(SQLServerDataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT APP_NAME(), DB_NAME()")) {
            assertThat(resultSet.next()).isTrue();
            return List.of(resultSet.getString(1), resultSet.getString(2));
        }
    }

    private static int spid(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return singleInt(statement, "SELECT @@SPID");
        }
    }

    private static int singleInt(Statement statement, String sql) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }

    /// Kills the session from the operator's connection and waits until the server has
    /// removed it, so the pooled socket is closed before the application uses it again.
    private static void killSession(int spid) throws Exception {
        try (Connection connection = admin.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("KILL " + spid);
            long deadline = System.nanoTime() + KILL_TIMEOUT.toNanos();
            while (System.nanoTime() < deadline) {
                if (singleInt(statement, "SELECT COUNT(*) FROM sys.dm_exec_sessions WHERE session_id = " + spid) == 0) {
                    return;
                }
                Thread.sleep(200);
            }
        }
        throw new IllegalStateException("Session " + spid + " was not removed in time");
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
