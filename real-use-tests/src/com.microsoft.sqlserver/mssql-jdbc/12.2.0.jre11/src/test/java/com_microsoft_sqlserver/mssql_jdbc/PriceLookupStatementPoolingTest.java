/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_microsoft_sqlserver.mssql_jdbc;

import com.microsoft.sqlserver.jdbc.ISQLServerConnection;
import com.microsoft.sqlserver.jdbc.ISQLServerPreparedStatement;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.microsoft.sqlserver.jdbc.SQLServerException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Tunes server-side prepared statement handle management: lazy preparation,
/// the per-connection statement pool, and batched unprepare of discarded handles.
///
/// The scenario is a price-lookup service that runs the same parameterized
/// queries thousands of times per connection. It configures
/// `enablePrepareOnFirstPreparedStatementCall`, `prepareMethod`,
/// `disableStatementPooling`, `statementPoolingCacheSize`, and
/// `serverPreparedStatementDiscardThreshold` on the data source, reads the handles the driver obtains through
/// `ISQLServerPreparedStatement.getPreparedStatementHandle`, and proves against a
/// real SQL Server, by running `sp_execute` on those handles, which of them are
/// still prepared on the session.
public class PriceLookupStatementPoolingTest {

    private static final String IMAGE = "mcr.microsoft.com/mssql/server:2022-CU14-ubuntu-22.04";

    private static final String PASSWORD = "PriceLookup#2024";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(180);

    private static final String CONTAINER = "real-use-mssql-stmtpool-" + UUID.randomUUID();

    private static final String PRICE_BY_SKU = "SELECT unit_price FROM dbo.product WHERE sku = ?";

    private static final String NAME_BY_SKU = "SELECT name FROM dbo.product WHERE sku = ?";

    /// SQL Server error raised when `sp_execute` names a handle the session does not hold.
    private static final int UNKNOWN_PREPARED_HANDLE = 8179;

    private static int port;

    @BeforeAll
    static void startServer() throws Exception {
        docker("run", "-d", "--name", CONTAINER, "-e", "ACCEPT_EULA=Y", "-e", "MSSQL_SA_PASSWORD=" + PASSWORD,
                "-p", "127.0.0.1::1433", IMAGE);
        String mapping = docker("port", CONTAINER, "1433/tcp").lines().findFirst().orElseThrow();
        port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));
        awaitServer();
        createSchema();
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @Test
    void firstExecutionRunsDirectlyAndRepeatedExecutionsShareOneSessionHandle() throws Exception {
        SQLServerDataSource defaults = dataSource();
        try (Connection connection = defaults.getConnection();
                PreparedStatement lookup = connection.prepareStatement(PRICE_BY_SKU)) {
            ISQLServerPreparedStatement sqlServerLookup = lookup.unwrap(ISQLServerPreparedStatement.class);
            assertThat(connection.unwrap(ISQLServerConnection.class).getEnablePrepareOnFirstPreparedStatementCall())
                    .isFalse();
            assertThat(sqlServerLookup.getPreparedStatementHandle()).isZero();

            assertThat(price(lookup, "SKU-LAMP")).isEqualByComparingTo("39.90");
            assertThat(sqlServerLookup.getPreparedStatementHandle()).isZero();

            assertThat(price(lookup, "SKU-DESK")).isEqualByComparingTo("249.00");
            int handle = sqlServerLookup.getPreparedStatementHandle();
            assertThat(handle).isPositive();

            assertThat(price(lookup, "SKU-CHAIR")).isEqualByComparingTo("129.50");
            assertThat(sqlServerLookup.getPreparedStatementHandle()).isEqualTo(handle);

            assertThat(executePrepared(connection, handle, "SKU-CHAIR")).isEqualTo("129.50");
            try (Connection otherSession = defaults.getConnection()) {
                assertUnknownHandle(otherSession, handle);
            }
        }
    }

    @Test
    void prepareOnFirstCallAndSpPrepareObtainHandleOnFirstExecution() throws Exception {
        SQLServerDataSource eager = dataSource();
        eager.setEnablePrepareOnFirstPreparedStatementCall(true);
        try (Connection connection = eager.getConnection();
                PreparedStatement lookup = connection.prepareStatement(PRICE_BY_SKU)) {
            ISQLServerPreparedStatement sqlServerLookup = lookup.unwrap(ISQLServerPreparedStatement.class);
            assertThat(connection.unwrap(ISQLServerConnection.class).getPrepareMethod()).isEqualTo("prepexec");

            assertThat(price(lookup, "SKU-LAMP")).isEqualByComparingTo("39.90");
            int handle = sqlServerLookup.getPreparedStatementHandle();
            assertThat(handle).isPositive();
            assertThat(executePrepared(connection, handle, "SKU-DESK")).isEqualTo("249.00");
        }

        SQLServerDataSource separatePrepare = dataSource();
        separatePrepare.setEnablePrepareOnFirstPreparedStatementCall(true);
        separatePrepare.setPrepareMethod("prepare");
        try (Connection connection = separatePrepare.getConnection();
                PreparedStatement lookup = connection.prepareStatement(NAME_BY_SKU)) {
            ISQLServerPreparedStatement sqlServerLookup = lookup.unwrap(ISQLServerPreparedStatement.class);
            assertThat(connection.unwrap(ISQLServerConnection.class).getPrepareMethod()).isEqualTo("prepare");

            assertThat(name(lookup, "SKU-DESK")).isEqualTo("Standing desk");
            int handle = sqlServerLookup.getPreparedStatementHandle();
            assertThat(handle).isPositive();

            assertThat(name(lookup, "SKU-CHAIR")).isEqualTo("Task chair");
            assertThat(sqlServerLookup.getPreparedStatementHandle()).isEqualTo(handle);
            assertThat(executePrepared(connection, handle, "SKU-LAMP")).isEqualTo("Desk lamp");
        }
    }

    @Test
    void statementPoolHandsTheSameHandleToEveryStatementForTheSameQuery() throws Exception {
        SQLServerDataSource pooled = dataSource();
        pooled.setEnablePrepareOnFirstPreparedStatementCall(true);
        pooled.setDisableStatementPooling(false);
        pooled.setStatementPoolingCacheSize(8);
        try (Connection connection = pooled.getConnection()) {
            ISQLServerConnection sqlServerConnection = connection.unwrap(ISQLServerConnection.class);
            assertThat(sqlServerConnection.isStatementPoolingEnabled()).isTrue();
            assertThat(sqlServerConnection.getStatementPoolingCacheSize()).isEqualTo(8);
            assertThat(sqlServerConnection.getStatementHandleCacheEntryCount()).isZero();

            int priceHandle;
            try (PreparedStatement firstRequest = connection.prepareStatement(PRICE_BY_SKU)) {
                assertThat(price(firstRequest, "SKU-LAMP")).isEqualByComparingTo("39.90");
                priceHandle = firstRequest.unwrap(ISQLServerPreparedStatement.class).getPreparedStatementHandle();
            }
            assertThat(priceHandle).isPositive();
            assertThat(sqlServerConnection.getStatementHandleCacheEntryCount()).isEqualTo(1);
            assertThat(sqlServerConnection.getDiscardedServerPreparedStatementCount()).isZero();

            try (PreparedStatement secondRequest = connection.prepareStatement(PRICE_BY_SKU)) {
                assertThat(price(secondRequest, "SKU-DESK")).isEqualByComparingTo("249.00");
                assertThat(secondRequest.unwrap(ISQLServerPreparedStatement.class).getPreparedStatementHandle())
                        .isEqualTo(priceHandle);
            }

            int nameHandle;
            try (PreparedStatement nameRequest = connection.prepareStatement(NAME_BY_SKU)) {
                assertThat(name(nameRequest, "SKU-CHAIR")).isEqualTo("Task chair");
                nameHandle = nameRequest.unwrap(ISQLServerPreparedStatement.class).getPreparedStatementHandle();
            }
            assertThat(nameHandle).isPositive().isNotEqualTo(priceHandle);
            assertThat(sqlServerConnection.getStatementHandleCacheEntryCount()).isEqualTo(2);

            assertThat(executePrepared(connection, priceHandle, "SKU-CHAIR")).isEqualTo("129.50");
            assertThat(executePrepared(connection, nameHandle, "SKU-LAMP")).isEqualTo("Desk lamp");
        }

        try (Connection freshSession = pooled.getConnection()) {
            assertThat(freshSession.unwrap(ISQLServerConnection.class).getStatementHandleCacheEntryCount()).isZero();
        }
    }

    @Test
    void handleEvictedFromAFullStatementPoolIsUnpreparedOnDemand() throws Exception {
        SQLServerDataSource singleEntryPool = dataSource();
        singleEntryPool.setEnablePrepareOnFirstPreparedStatementCall(true);
        singleEntryPool.setDisableStatementPooling(false);
        singleEntryPool.setStatementPoolingCacheSize(1);
        try (Connection connection = singleEntryPool.getConnection()) {
            ISQLServerConnection sqlServerConnection = connection.unwrap(ISQLServerConnection.class);

            int priceHandle;
            try (PreparedStatement priceRequest = connection.prepareStatement(PRICE_BY_SKU)) {
                assertThat(price(priceRequest, "SKU-LAMP")).isEqualByComparingTo("39.90");
                priceHandle = priceRequest.unwrap(ISQLServerPreparedStatement.class).getPreparedStatementHandle();
            }
            int nameHandle;
            try (PreparedStatement nameRequest = connection.prepareStatement(NAME_BY_SKU)) {
                assertThat(name(nameRequest, "SKU-DESK")).isEqualTo("Standing desk");
                nameHandle = nameRequest.unwrap(ISQLServerPreparedStatement.class).getPreparedStatementHandle();
            }

            assertThat(sqlServerConnection.getStatementHandleCacheEntryCount()).isEqualTo(1);
            assertThat(sqlServerConnection.getDiscardedServerPreparedStatementCount()).isEqualTo(1);
            assertThat(executePrepared(connection, priceHandle, "SKU-DESK")).isEqualTo("249.00");

            sqlServerConnection.closeUnreferencedPreparedStatementHandles();

            assertThat(sqlServerConnection.getDiscardedServerPreparedStatementCount()).isZero();
            assertUnknownHandle(connection, priceHandle);
            assertThat(executePrepared(connection, nameHandle, "SKU-CHAIR")).isEqualTo("Task chair");
        }
    }

    @Test
    void closedStatementHandlesAreUnpreparedInBatchesOnceTheDiscardThresholdIsExceeded() throws Exception {
        SQLServerDataSource batching = dataSource();
        batching.setEnablePrepareOnFirstPreparedStatementCall(true);
        batching.setServerPreparedStatementDiscardThreshold(3);
        try (Connection connection = batching.getConnection()) {
            ISQLServerConnection sqlServerConnection = connection.unwrap(ISQLServerConnection.class);
            assertThat(sqlServerConnection.isStatementPoolingEnabled()).isFalse();
            assertThat(sqlServerConnection.getServerPreparedStatementDiscardThreshold()).isEqualTo(3);

            List<Integer> handles = new ArrayList<>();
            for (String region : List.of("north", "south", "east")) {
                handles.add(closeAfterRegionalLookup(connection, region));
                assertThat(sqlServerConnection.getDiscardedServerPreparedStatementCount()).isEqualTo(handles.size());
            }
            for (int handle : handles) {
                assertThat(executePrepared(connection, handle, "SKU-LAMP")).isEqualTo("39.90");
            }

            handles.add(closeAfterRegionalLookup(connection, "west"));

            assertThat(sqlServerConnection.getDiscardedServerPreparedStatementCount()).isZero();
            for (int handle : handles) {
                assertUnknownHandle(connection, handle);
            }
        }

        SQLServerDataSource immediate = dataSource();
        immediate.setEnablePrepareOnFirstPreparedStatementCall(true);
        immediate.setServerPreparedStatementDiscardThreshold(1);
        try (Connection connection = immediate.getConnection()) {
            int handle = closeAfterRegionalLookup(connection, "central");

            assertThat(connection.unwrap(ISQLServerConnection.class).getDiscardedServerPreparedStatementCount())
                    .isZero();
            assertUnknownHandle(connection, handle);
        }
    }

    /// Runs one regional price query on its own statement and closes it, returning the handle it held.
    private static int closeAfterRegionalLookup(Connection connection, String region) throws SQLException {
        String sql = "SELECT unit_price FROM dbo.product WHERE sku = ? AND N'" + region + "' <> N''";
        try (PreparedStatement lookup = connection.prepareStatement(sql)) {
            assertThat(price(lookup, "SKU-LAMP")).isEqualByComparingTo("39.90");
            int handle = lookup.unwrap(ISQLServerPreparedStatement.class).getPreparedStatementHandle();
            assertThat(handle).isPositive();
            return handle;
        }
    }

    private static BigDecimal price(PreparedStatement lookup, String sku) throws SQLException {
        lookup.setString(1, sku);
        BigDecimal price;
        try (ResultSet row = lookup.executeQuery()) {
            assertThat(row.next()).isTrue();
            price = row.getBigDecimal(1);
        }
        drainResults(lookup);
        return price;
    }

    private static String name(PreparedStatement lookup, String sku) throws SQLException {
        lookup.setString(1, sku);
        String name;
        try (ResultSet row = lookup.executeQuery()) {
            assertThat(row.next()).isTrue();
            name = row.getString(1);
        }
        drainResults(lookup);
        return name;
    }

    /// Reads the rest of the response, where the server returns the prepared handle after the rows.
    private static void drainResults(PreparedStatement lookup) throws SQLException {
        assertThat(lookup.getMoreResults()).isFalse();
        assertThat(lookup.getUpdateCount()).isEqualTo(-1);
    }

    /// Executes a session-scoped prepared handle directly with `sp_execute`, as a DBA would.
    private static String executePrepared(Connection connection, int handle, String sku) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery("EXEC sp_execute " + handle + ", N'" + sku + "'")) {
            assertThat(row.next()).isTrue();
            return row.getString(1);
        }
    }

    private static void assertUnknownHandle(Connection connection, int handle) {
        assertThatThrownBy(() -> executePrepared(connection, handle, "SKU-LAMP"))
                .isInstanceOf(SQLServerException.class)
                .satisfies(e -> assertThat(((SQLServerException) e).getErrorCode())
                        .isEqualTo(UNKNOWN_PREPARED_HANDLE));
    }

    private static SQLServerDataSource dataSource() {
        SQLServerDataSource dataSource = new SQLServerDataSource();
        dataSource.setServerName("127.0.0.1");
        dataSource.setPortNumber(port);
        dataSource.setUser("sa");
        dataSource.setPassword(PASSWORD);
        dataSource.setEncrypt("false");
        dataSource.setApplicationName("price-lookup-service");
        dataSource.setLoginTimeout(5);
        return dataSource;
    }

    private static void createSchema() throws SQLException {
        List<String> ddl = List.of(
                """
                CREATE TABLE dbo.product (
                    sku NVARCHAR(32) PRIMARY KEY,
                    name NVARCHAR(100) NOT NULL,
                    unit_price DECIMAL(10, 2) NOT NULL)
                """,
                """
                INSERT INTO dbo.product (sku, name, unit_price) VALUES
                    ('SKU-LAMP', 'Desk lamp', 39.90),
                    ('SKU-DESK', 'Standing desk', 249.00),
                    ('SKU-CHAIR', 'Task chair', 129.50)
                """);
        try (Connection connection = dataSource().getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : ddl) {
                statement.execute(sql);
            }
        }
    }

    private static void awaitServer() throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        SQLException last = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = dataSource().getConnection();
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
