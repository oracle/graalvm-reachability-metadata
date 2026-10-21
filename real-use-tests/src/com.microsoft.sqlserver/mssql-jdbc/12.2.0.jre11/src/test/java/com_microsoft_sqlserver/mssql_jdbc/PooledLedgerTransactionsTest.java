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
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Drives JDBC transaction management through pooled driver connections:
/// commit and rollback, savepoints, snapshot isolation, lock timeouts,
/// batched updates, and the session reset a pool performs between borrowers.
///
/// The scenario is a payment ledger that borrows connections from a HikariCP
/// pool built on the driver's JDBC URL and moves money between accounts
/// against a real SQL Server.
public class PooledLedgerTransactionsTest {

    private static final String IMAGE = "mcr.microsoft.com/mssql/server:2022-CU14-ubuntu-22.04";

    private static final String PASSWORD = "Ledger#Pool2024";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(180);

    private static final String CONTAINER = "real-use-mssql-ledger-" + UUID.randomUUID();

    private static final int CHECK_CONSTRAINT_VIOLATION = 547;

    private static final int LOCK_REQUEST_TIMEOUT = 1222;

    private static final int DUPLICATE_KEY = 2627;

    private static String jdbcUrl;

    private static HikariDataSource pool;

    @BeforeAll
    static void startServer() throws Exception {
        docker("run", "-d", "--name", CONTAINER, "-e", "ACCEPT_EULA=Y", "-e", "MSSQL_SA_PASSWORD=" + PASSWORD,
                "-p", "127.0.0.1::1433", IMAGE);
        String mapping = docker("port", CONTAINER, "1433/tcp").lines().findFirst().orElseThrow();
        int port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));

        SQLServerDataSource admin = new SQLServerDataSource();
        admin.setServerName("127.0.0.1");
        admin.setPortNumber(port);
        admin.setUser("sa");
        admin.setPassword(PASSWORD);
        admin.setEncrypt("false");
        admin.setLoginTimeout(5);
        awaitServer(admin);
        try (Connection connection = admin.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE ledger");
            statement.execute("ALTER DATABASE ledger SET ALLOW_SNAPSHOT_ISOLATION ON");
        }

        jdbcUrl = "jdbc:sqlserver://127.0.0.1:%d;databaseName=ledger;encrypt=false;applicationName=ledger"
                .formatted(port);
        pool = newPool("ledger-pool", 4);
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (pool != null) {
            pool.close();
        }
        docker("rm", "-f", "-v", CONTAINER);
    }

    @Test
    void transferCommitsBothLegsAndRollsBackAnOverdraftAsAWhole() throws Exception {
        seedAccounts("transfer_accounts");
        try (Connection connection = pool.getConnection()) {
            connection.setAutoCommit(false);
            transfer(connection, "transfer_accounts", "alice", "bob", "40.00");
            connection.commit();

            assertThatThrownBy(() -> transfer(connection, "transfer_accounts", "bob", "carol", "500.00"))
                    .isInstanceOfSatisfying(SQLServerException.class, e -> {
                        assertThat(e.getErrorCode()).isEqualTo(CHECK_CONSTRAINT_VIOLATION);
                        assertThat(e.getSQLServerError().getErrorNumber()).isEqualTo(CHECK_CONSTRAINT_VIOLATION);
                    });
            connection.rollback();
            connection.setAutoCommit(true);
        }

        assertThat(balances("transfer_accounts")).containsExactly(
                Map.entry("alice", new BigDecimal("60.00")),
                Map.entry("bob", new BigDecimal("90.00")),
                Map.entry("carol", new BigDecimal("10.00")));
    }

    @Test
    void savepointDiscardsOnlyTheFailedFeeLeg() throws Exception {
        seedAccounts("fee_accounts");
        try (Connection connection = pool.getConnection()) {
            connection.setAutoCommit(false);
            transfer(connection, "fee_accounts", "carol", "alice", "5.00");

            Savepoint beforeFee = connection.setSavepoint("before_fee");
            assertThat(beforeFee.getSavepointName()).isEqualTo("before_fee");
            debit(connection, "fee_accounts", "carol", "2.50");
            assertThatThrownBy(() -> debit(connection, "fee_accounts", "carol", "20.00"))
                    .isInstanceOfSatisfying(SQLServerException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(CHECK_CONSTRAINT_VIOLATION));
            connection.rollback(beforeFee);

            Savepoint unnamed = connection.setSavepoint();
            assertThat(unnamed.getSavepointId()).isPositive();
            debit(connection, "fee_accounts", "alice", "1.00");
            connection.commit();
            connection.setAutoCommit(true);
        }

        assertThat(balances("fee_accounts")).containsExactly(
                Map.entry("alice", new BigDecimal("104.00")),
                Map.entry("bob", new BigDecimal("50.00")),
                Map.entry("carol", new BigDecimal("5.00")));
    }

    @Test
    void snapshotReaderSeesCommittedBalanceWhileAReadCommittedReaderTimesOut() throws Exception {
        seedAccounts("snapshot_accounts");
        try (Connection writer = pool.getConnection()) {
            writer.setAutoCommit(false);
            debit(writer, "snapshot_accounts", "alice", "70.00");

            try (Connection reader = pool.getConnection()) {
                reader.setTransactionIsolation(ISQLServerConnection.TRANSACTION_SNAPSHOT);
                reader.setAutoCommit(false);
                assertThat(reader.getTransactionIsolation()).isEqualTo(ISQLServerConnection.TRANSACTION_SNAPSHOT);
                assertThat(balanceOf(reader, "snapshot_accounts", "alice")).isEqualByComparingTo("100.00");
                reader.commit();
                reader.setAutoCommit(true);
                reader.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            }

            try (Connection blocked = pool.getConnection(); Statement session = blocked.createStatement()) {
                session.execute("SET LOCK_TIMEOUT 500");
                try {
                    assertThatThrownBy(() -> balanceOf(blocked, "snapshot_accounts", "alice"))
                            .isInstanceOfSatisfying(SQLServerException.class,
                                    e -> assertThat(e.getErrorCode()).isEqualTo(LOCK_REQUEST_TIMEOUT));
                } finally {
                    session.execute("SET LOCK_TIMEOUT -1");
                }
            }

            writer.commit();
            writer.setAutoCommit(true);
        }

        assertThat(balances("snapshot_accounts")).containsEntry("alice", new BigDecimal("30.00"));
    }

    @Test
    void batchedJournalEntriesReportTheRowThatFailed() throws Exception {
        try (Connection connection = pool.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE dbo.journal (
                        entry_id INT PRIMARY KEY,
                        account NVARCHAR(20) NOT NULL,
                        amount DECIMAL(10, 2) NOT NULL)
                    """);
        }

        try (Connection connection = pool.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO dbo.journal (entry_id, account, amount) VALUES (?, ?, ?)")) {
                addEntry(insert, 1, "alice", "-40.00");
                addEntry(insert, 2, "bob", "40.00");
                assertThat(insert.executeBatch()).containsExactly(1, 1);
                connection.commit();

                addEntry(insert, 3, "carol", "-5.00");
                addEntry(insert, 1, "alice", "5.00");
                addEntry(insert, 4, "alice", "5.00");
                assertThatThrownBy(insert::executeBatch).isInstanceOfSatisfying(BatchUpdateException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(DUPLICATE_KEY);
                    assertThat(e.getUpdateCounts()).containsExactly(1, Statement.EXECUTE_FAILED, 1);
                });
                connection.rollback();
            }
            connection.setAutoCommit(true);
        }

        try (Connection connection = pool.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT entry_id, account FROM dbo.journal ORDER BY entry_id")) {
            Map<Integer, String> entries = new LinkedHashMap<>();
            while (rows.next()) {
                entries.put(rows.getInt("entry_id"), rows.getString("account"));
            }
            assertThat(entries).containsExactly(Map.entry(1, "alice"), Map.entry(2, "bob"));
        }
    }

    @Test
    void poolRollsBackAndResetsASessionBeforeTheNextBorrower() throws Exception {
        seedAccounts("reset_accounts");
        try (HikariDataSource single = newPool("ledger-single", 1)) {
            UUID firstSession;
            try (Connection careless = single.getConnection()) {
                firstSession = careless.unwrap(ISQLServerConnection.class).getClientConnectionId();
                careless.setAutoCommit(false);
                careless.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                debit(careless, "reset_accounts", "bob", "50.00");
                assertThat(sessionIsolationLevel(careless)).isEqualTo(4);
            }

            try (Connection next = single.getConnection()) {
                assertThat(next.unwrap(ISQLServerConnection.class).getClientConnectionId()).isEqualTo(firstSession);
                assertThat(next.getAutoCommit()).isTrue();
                assertThat(next.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
                assertThat(sessionIsolationLevel(next)).isEqualTo(2);
                assertThat(balanceOf(next, "reset_accounts", "bob")).isEqualByComparingTo("50.00");
            }

            assertThat(single.getHikariPoolMXBean().getTotalConnections()).isOne();
        }
    }

    private static HikariDataSource newPool(String name, int size) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(name);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername("sa");
        config.setPassword(PASSWORD);
        config.setMaximumPoolSize(size);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000);
        config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        return new HikariDataSource(config);
    }

    private static void seedAccounts(String table) throws SQLException {
        try (Connection connection = pool.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE dbo.%s (
                        owner NVARCHAR(20) PRIMARY KEY,
                        balance DECIMAL(10, 2) NOT NULL CHECK (balance >= 0))
                    """.formatted(table));
            statement.execute("""
                    INSERT INTO dbo.%s (owner, balance) VALUES
                        ('alice', 100.00),
                        ('bob', 50.00),
                        ('carol', 10.00)
                    """.formatted(table));
        }
    }

    private static void transfer(Connection connection, String table, String from, String to, String amount)
            throws SQLException {
        credit(connection, table, to, amount);
        debit(connection, table, from, amount);
    }

    private static void credit(Connection connection, String table, String owner, String amount) throws SQLException {
        adjust(connection, table, owner, new BigDecimal(amount));
    }

    private static void debit(Connection connection, String table, String owner, String amount) throws SQLException {
        adjust(connection, table, owner, new BigDecimal(amount).negate());
    }

    private static void adjust(Connection connection, String table, String owner, BigDecimal delta)
            throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE dbo.%s SET balance = balance + ? WHERE owner = ?".formatted(table))) {
            update.setBigDecimal(1, delta);
            update.setString(2, owner);
            assertThat(update.executeUpdate()).isOne();
        }
    }

    private static void addEntry(PreparedStatement insert, int id, String account, String amount)
            throws SQLException {
        insert.setInt(1, id);
        insert.setString(2, account);
        insert.setBigDecimal(3, new BigDecimal(amount));
        insert.addBatch();
    }

    private static BigDecimal balanceOf(Connection connection, String table, String owner) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT balance FROM dbo.%s WHERE owner = ?".formatted(table))) {
            query.setString(1, owner);
            try (ResultSet row = query.executeQuery()) {
                assertThat(row.next()).isTrue();
                return row.getBigDecimal("balance");
            }
        }
    }

    private static Map<String, BigDecimal> balances(String table) throws SQLException {
        Map<String, BigDecimal> balances = new LinkedHashMap<>();
        try (Connection connection = pool.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT owner, balance FROM dbo.%s ORDER BY owner".formatted(table))) {
            while (rows.next()) {
                balances.put(rows.getString("owner"), rows.getBigDecimal("balance"));
            }
        }
        return balances;
    }

    private static int sessionIsolationLevel(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery(
                        "SELECT transaction_isolation_level FROM sys.dm_exec_sessions WHERE session_id = @@SPID")) {
            assertThat(row.next()).isTrue();
            return row.getInt(1);
        }
    }

    private static void awaitServer(SQLServerDataSource admin) throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        SQLException last = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = admin.getConnection(); Statement statement = connection.createStatement()) {
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
