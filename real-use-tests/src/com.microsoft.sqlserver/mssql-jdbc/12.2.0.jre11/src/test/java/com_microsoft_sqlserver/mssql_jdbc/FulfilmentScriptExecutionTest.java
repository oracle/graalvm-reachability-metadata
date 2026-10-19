/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_microsoft_sqlserver.mssql_jdbc;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.microsoft.sqlserver.jdbc.SQLServerException;
import com.microsoft.sqlserver.jdbc.SQLServerStatement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Drives statement execution of multi-statement T-SQL batches: interleaved
/// result sets and update counts, server info messages as the SQLWarning chain,
/// query timeouts, cross-thread cancellation, row limits, and JDBC escape syntax.
///
/// The scenario is a fulfilment job runner that sends whole T-SQL scripts to a
/// real SQL Server and reports what each script produced, the messages it
/// printed, and how long-running scripts were stopped.
public class FulfilmentScriptExecutionTest {

    private static final String IMAGE = "mcr.microsoft.com/mssql/server:2022-CU14-ubuntu-22.04";

    private static final String PASSWORD = "Fulfil#Scripts2024";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(180);

    private static final Duration CANCEL_WAIT = Duration.ofSeconds(30);

    private static final String CONTAINER = "real-use-mssql-scripts-" + UUID.randomUUID();

    private static final int RAISERROR_USER_MESSAGE = 50000;

    private static SQLServerDataSource dataSource;

    @BeforeAll
    static void startServer() throws Exception {
        docker("run", "-d", "--name", CONTAINER, "-e", "ACCEPT_EULA=Y", "-e", "MSSQL_SA_PASSWORD=" + PASSWORD,
                "-p", "127.0.0.1::1433", IMAGE);
        String mapping = docker("port", CONTAINER, "1433/tcp").lines().findFirst().orElseThrow();
        int port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));

        dataSource = new SQLServerDataSource();
        dataSource.setServerName("127.0.0.1");
        dataSource.setPortNumber(port);
        dataSource.setUser("sa");
        dataSource.setPassword(PASSWORD);
        dataSource.setEncrypt("false");
        dataSource.setApplicationName("fulfilment-jobs");
        dataSource.setLoginTimeout(5);
        awaitServer();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE fulfilment");
        }
        dataSource.setDatabaseName("fulfilment");
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @Test
    void scriptReportsEveryUpdateCountAndResultSetInOrder() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE dbo.shipments (id INT PRIMARY KEY, sku NVARCHAR(20) NOT NULL, "
                    + "state NVARCHAR(10) NOT NULL)");

            boolean isResultSet = statement.execute("""
                    INSERT INTO dbo.shipments VALUES (1, N'WIDGET', N'NEW'), (2, N'GADGET', N'NEW'),
                                                     (3, N'GIZMO', N'NEW');
                    SELECT id, sku FROM dbo.shipments WHERE state = N'NEW' ORDER BY id;
                    UPDATE dbo.shipments SET state = N'PICKED' WHERE id IN (1, 3);
                    DELETE FROM dbo.shipments WHERE id = 42;
                    SELECT state, COUNT(*) AS shipments FROM dbo.shipments GROUP BY state ORDER BY state;
                    """);

            List<String> outcomes = outcomes(statement, isResultSet);
            assertThat(outcomes).containsExactly(
                    "updated 3",
                    "rows[1=WIDGET, 2=GADGET, 3=GIZMO]",
                    "updated 2",
                    "updated 0",
                    "rows[NEW=1, PICKED=2]");
        }
    }

    @Test
    void nocountScriptHidesItsDataModificationCounts() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            boolean isResultSet = statement.execute("""
                    SET NOCOUNT ON;
                    CREATE TABLE #picks (sku NVARCHAR(20) NOT NULL, quantity INT NOT NULL);
                    INSERT INTO #picks VALUES (N'WIDGET', 4), (N'GADGET', 1), (N'WIDGET', 2);
                    UPDATE #picks SET quantity = quantity * 10;
                    SELECT sku, SUM(quantity) AS total FROM #picks GROUP BY sku ORDER BY sku;
                    """);

            assertThat(outcomes(statement, isResultSet))
                    .as("DDL still reports a zero count while NOCOUNT hides the DML counts")
                    .containsExactly("updated 0", "rows[GADGET=10, WIDGET=60]");
        }
    }

    @Test
    void printedProgressAndLowSeverityErrorsArriveAsWarnings() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    PRINT 'picking wave 7 started';
                    RAISERROR('stock for %s is below %d units', 10, 1, 'WIDGET', 5);
                    SELECT 'wave 7' AS wave;
                    """);
            drainResults(statement);

            List<SQLWarning> warnings = new ArrayList<>();
            for (SQLWarning warning = statement.getWarnings(); warning != null; warning = warning.getNextWarning()) {
                warnings.add(warning);
            }
            assertThat(warnings).hasSize(2);
            assertThat(warnings.get(0).getMessage()).isEqualTo("picking wave 7 started");
            assertThat(warnings.get(0).getErrorCode()).isZero();
            assertThat(warnings.get(1).getMessage()).isEqualTo("stock for WIDGET is below 5 units");
            assertThat(warnings.get(1).getErrorCode()).isEqualTo(RAISERROR_USER_MESSAGE);

            statement.clearWarnings();
            assertThat((Throwable) statement.getWarnings()).isNull();

            assertThatThrownBy(() -> statement.execute("RAISERROR('wave 7 aborted: carrier %s offline', 16, 1, 'ACME')"))
                    .isInstanceOfSatisfying(SQLServerException.class, e -> {
                        assertThat(e.getErrorCode()).isEqualTo(RAISERROR_USER_MESSAGE);
                        assertThat(e.getMessage()).isEqualTo("wave 7 aborted: carrier ACME offline");
                        assertThat(e.getSQLServerError().getErrorSeverity()).isEqualTo(16);
                    });
        }
    }

    @Test
    void queryTimeoutStopsAStuckScriptAndKeepsTheConnectionUsable() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(1);
            assertThat(statement.getQueryTimeout()).isEqualTo(1);

            assertThatThrownBy(() -> statement.executeQuery("WAITFOR DELAY '00:01:00'; SELECT 1"))
                    .isInstanceOf(SQLTimeoutException.class)
                    .hasMessageContaining("timed out");

            statement.setQueryTimeout(0);
            try (ResultSet alive = statement.executeQuery("SELECT 'still connected'")) {
                assertThat(alive.next()).isTrue();
                assertThat(alive.getString(1)).isEqualTo("still connected");
            }
        }
    }

    @Test
    void operatorCancelsARunningScriptFromAnotherThread() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (Connection jobConnection = dataSource.getConnection();
                Statement job = jobConnection.createStatement();
                Connection monitorConnection = dataSource.getConnection()) {
            int jobSession = sessionId(jobConnection);

            Future<Boolean> running = worker.submit(() -> {
                try (ResultSet rows = job.executeQuery("WAITFOR DELAY '00:05:00'; SELECT 1")) {
                    return rows.next();
                }
            });
            awaitWaitFor(monitorConnection, jobSession);

            job.cancel();

            assertThatThrownBy(() -> running.get(CANCEL_WAIT.toSeconds(), TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .getCause()
                    .isInstanceOf(SQLServerException.class)
                    .hasMessageContaining("canceled");

            try (ResultSet alive = job.executeQuery("SELECT @@SPID")) {
                assertThat(alive.next()).isTrue();
                assertThat(alive.getInt(1)).isEqualTo(jobSession);
            }
        } finally {
            worker.shutdownNow();
            assertThat(worker.awaitTermination(CANCEL_WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void reportLimitsRowsAndRespondsAdaptively() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement setup = connection.createStatement()) {
            setup.execute("CREATE TABLE dbo.carriers (code NVARCHAR(10) PRIMARY KEY, rating INT NOT NULL)");
            setup.executeUpdate("INSERT INTO dbo.carriers VALUES (N'ACME', 4), (N'BOLT', 5), (N'CRAB', 2), "
                    + "(N'DART', 3), (N'EMU', 1)");

            try (SQLServerStatement statement = connection.createStatement().unwrap(SQLServerStatement.class)) {
                statement.setResponseBuffering("adaptive");
                assertThat(statement.getResponseBuffering()).isEqualTo("adaptive");
                statement.setMaxRows(2);
                statement.setFetchSize(1);
                List<String> best = new ArrayList<>();
                try (ResultSet rows = statement.executeQuery("SELECT code FROM dbo.carriers ORDER BY rating DESC")) {
                    while (rows.next()) {
                        best.add(rows.getString(1));
                    }
                }
                assertThat(best).containsExactly("BOLT", "ACME");

                statement.setMaxRows(0);
                long demoted = statement.executeLargeUpdate("UPDATE dbo.carriers SET rating = rating - 1 "
                        + "WHERE rating < 3");
                assertThat(demoted).isEqualTo(2L);
            }
        }
    }

    @Test
    void escapeSyntaxIsTranslatedToTransactSql() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement setup = connection.createStatement()) {
            setup.execute("CREATE TABLE dbo.waves (id INT PRIMARY KEY, code NVARCHAR(20) NOT NULL, "
                    + "planned DATE NOT NULL)");
            setup.execute("CREATE TABLE dbo.wave_notes (wave_id INT NOT NULL, note NVARCHAR(40) NOT NULL)");
            setup.executeUpdate("INSERT INTO dbo.waves VALUES (1, N'north_7', '2024-03-01'), "
                    + "(2, N'north-7', '2024-03-02'), (3, N'south_1', '2024-03-05')");
            setup.executeUpdate("INSERT INTO dbo.wave_notes VALUES (3, N'late truck')");

            try (PreparedStatement lookup = connection.prepareStatement("""
                    SELECT {fn UCASE(w.code)} AS code, {fn LENGTH(w.code)} AS code_length, n.note
                    FROM {oj dbo.waves w LEFT OUTER JOIN dbo.wave_notes n ON n.wave_id = w.id}
                    WHERE w.code LIKE ? {escape '!'}
                      AND w.planned >= {d '2024-03-01'}
                      AND w.planned < {fn TIMESTAMPADD(SQL_TSI_DAY, ?, {ts '2024-03-01 00:00:00'})}
                    ORDER BY w.planned
                    """)) {
                lookup.setString(1, "%!_%");
                lookup.setInt(2, 10);
                List<String> matches = new ArrayList<>();
                try (ResultSet rows = lookup.executeQuery()) {
                    while (rows.next()) {
                        matches.add(rows.getString("code") + "/" + rows.getInt("code_length") + "/"
                                + rows.getString("note"));
                    }
                }
                assertThat(matches).containsExactly("NORTH_7/7/null", "SOUTH_1/7/late truck");
            }

            try (PreparedStatement planned = connection.prepareStatement(
                    "SELECT planned FROM dbo.waves WHERE id = ? AND {fn DAYOFMONTH(planned)} = 5")) {
                planned.setInt(1, 3);
                try (ResultSet rows = planned.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    Date date = rows.getDate(1);
                    assertThat(date.toLocalDate()).isEqualTo(LocalDate.of(2024, 3, 5));
                }
            }
        }
    }

    /// Walks every result a script produced, rendering result sets as their
    /// first two columns and update counts as `updated n`.
    private static List<String> outcomes(Statement statement, boolean firstIsResultSet) throws SQLException {
        List<String> outcomes = new ArrayList<>();
        boolean isResultSet = firstIsResultSet;
        while (true) {
            if (isResultSet) {
                try (ResultSet rows = statement.getResultSet()) {
                    List<String> values = new ArrayList<>();
                    while (rows.next()) {
                        values.add(rows.getString(1) + "=" + rows.getString(2));
                    }
                    outcomes.add("rows" + values);
                }
            } else {
                int updateCount = statement.getUpdateCount();
                if (updateCount == -1) {
                    return outcomes;
                }
                outcomes.add("updated " + updateCount);
            }
            isResultSet = statement.getMoreResults();
        }
    }

    private static void drainResults(Statement statement) throws SQLException {
        boolean isResultSet = statement.getResultSet() != null;
        while (isResultSet || statement.getUpdateCount() != -1) {
            if (isResultSet) {
                try (ResultSet rows = statement.getResultSet()) {
                    while (rows.next()) {
                        assertThat(rows.getString(1)).isNotNull();
                    }
                }
            }
            isResultSet = statement.getMoreResults();
        }
    }

    private static int sessionId(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT @@SPID")) {
            assertThat(rows.next()).isTrue();
            return rows.getInt(1);
        }
    }

    /// Polls the server until the given session is blocked in its `WAITFOR`,
    /// so the cancel is sent while the script is provably running.
    private static void awaitWaitFor(Connection monitor, int session) throws Exception {
        long deadline = System.nanoTime() + CANCEL_WAIT.toNanos();
        try (PreparedStatement probe = monitor.prepareStatement(
                "SELECT COUNT(*) FROM sys.dm_exec_requests WHERE session_id = ? AND wait_type = 'WAITFOR'")) {
            probe.setInt(1, session);
            while (System.nanoTime() < deadline) {
                try (ResultSet rows = probe.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    if (rows.getInt(1) == 1) {
                        return;
                    }
                }
                Thread.sleep(100);
            }
        }
        throw new IllegalStateException("Session " + session + " never started waiting");
    }

    private static void awaitServer() throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        SQLException last = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
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
