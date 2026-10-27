/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mysql.mysql_connector_j;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Stored routines through `CallableStatement`: a ledger service pooled by HikariCP calls
/// procedures with IN, OUT and INOUT parameters, reads a stored function's return value, walks
/// the several result sets one procedure returns, and maps a `SIGNAL` raised by a procedure.
public class LedgerStoredProcedureTest {

    private static final String IMAGE = "container-registry.oracle.com/mysql/community-server:26.7.1";
    private static final String DATABASE = "ledger";
    private static final String USER = "teller";
    private static final String PASSWORD = "teller-secret";
    private static final String CONTAINER = "real-use-mysql-ledger-" + UUID.randomUUID();

    private static HikariDataSource pool;

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
                IMAGE, "--log-bin-trust-function-creators=1");
        String url = "jdbc:mysql://127.0.0.1:" + port + "/" + DATABASE;
        awaitServer(url, TimeUnit.MINUTES.toMillis(3));

        HikariConfig config = new HikariConfig();
        config.setPoolName("ledger-pool");
        config.setJdbcUrl(url);
        config.setUsername(USER);
        config.setPassword(PASSWORD);
        config.setMaximumPoolSize(2);
        config.addDataSourceProperty("cacheCallableStmts", "true");
        config.addDataSourceProperty("connectionTimeZone", "UTC");
        pool = new HikariDataSource(config);
        createSchema();
    }

    @AfterAll
    static void stopMysql() throws Exception {
        if (pool != null) {
            pool.close();
        }
        docker("rm", "-f", CONTAINER);
    }

    @Test
    void movesMoneyThroughStoredRoutines() throws SQLException {
        assertThat(openAccount("alice", new BigDecimal("1000.00"))).isEqualTo(1L);
        assertThat(openAccount("bob", new BigDecimal("250.00"))).isEqualTo(2L);
        assertThat(openAccount("carol", new BigDecimal("40.00"))).isEqualTo(3L);

        // The function's return value and the procedure's OUT parameter come back typed.
        assertThat(transferFee(new BigDecimal("300.00"))).isEqualByComparingTo("1.50");
        assertThat(transfer(1, 2, new BigDecimal("300.00"))).isEqualByComparingTo("698.50");

        // An INOUT parameter carries the balance in and the compounded balance back out.
        assertThat(compound(new BigDecimal("550.00"), 2)).isEqualByComparingTo("561.06");

        // A business rule enforced by SIGNAL surfaces as an SQLException with its own state.
        assertThatThrownBy(() -> transfer(3, 1, new BigDecimal("100.00")))
                .isInstanceOfSatisfying(SQLException.class, e -> {
                    assertThat(e.getSQLState()).isEqualTo("45000");
                    assertThat(e.getErrorCode()).isEqualTo(1644);
                    assertThat(e.getMessage()).isEqualTo("insufficient funds in account 3");
                });

        StatementReport report = statement(new BigDecimal("100.00"));
        Map<String, BigDecimal> expectedBalances = new LinkedHashMap<>();
        expectedBalances.put("alice", new BigDecimal("698.50"));
        expectedBalances.put("bob", new BigDecimal("550.00"));
        assertThat(report.balances()).containsOnlyKeys(expectedBalances.keySet());
        expectedBalances.forEach((owner, balance) ->
                assertThat(report.balances().get(owner)).isEqualByComparingTo(balance));
        assertThat(report.transfers()).containsExactly("alice->bob 300.00 fee 1.50");
        assertThat(report.totalHeld()).isEqualByComparingTo("1288.50");

        assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
    }

    /// The account and transfer totals the `account_statement` procedure returns as three
    /// consecutive result sets.
    record StatementReport(Map<String, BigDecimal> balances, List<String> transfers, BigDecimal totalHeld) {
    }

    private static long openAccount(String owner, BigDecimal deposit) throws SQLException {
        try (Connection connection = pool.getConnection();
             CallableStatement call = connection.prepareCall("{call open_account(?, ?, ?)}")) {
            call.setString(1, owner);
            call.setBigDecimal(2, deposit);
            call.registerOutParameter(3, Types.BIGINT);
            call.execute();
            return call.getLong(3);
        }
    }

    private static BigDecimal transferFee(BigDecimal amount) throws SQLException {
        try (Connection connection = pool.getConnection();
             CallableStatement call = connection.prepareCall("{? = call transfer_fee(?)}")) {
            call.registerOutParameter(1, Types.DECIMAL);
            call.setBigDecimal(2, amount);
            call.execute();
            return call.getBigDecimal(1);
        }
    }

    /// Moves `amount` plus the fee out of `source` and returns the source's new balance.
    private static BigDecimal transfer(long source, long target, BigDecimal amount) throws SQLException {
        try (Connection connection = pool.getConnection();
             CallableStatement call = connection.prepareCall("{call transfer(?, ?, ?, ?)}")) {
            call.setLong("source_id", source);
            call.setLong("target_id", target);
            call.setBigDecimal("amount", amount);
            call.registerOutParameter("source_balance", Types.DECIMAL);
            call.execute();
            return call.getBigDecimal("source_balance");
        }
    }

    private static BigDecimal compound(BigDecimal balance, int periods) throws SQLException {
        try (Connection connection = pool.getConnection();
             CallableStatement call = connection.prepareCall("{call compound_interest(?, ?)}")) {
            call.setBigDecimal(1, balance);
            call.registerOutParameter(1, Types.DECIMAL);
            call.setInt(2, periods);
            call.execute();
            return call.getBigDecimal(1);
        }
    }

    private static StatementReport statement(BigDecimal minimumBalance) throws SQLException {
        try (Connection connection = pool.getConnection();
             CallableStatement call = connection.prepareCall("{call account_statement(?)}")) {
            call.setBigDecimal(1, minimumBalance);
            assertThat(call.execute()).isTrue();

            Map<String, BigDecimal> balances = new LinkedHashMap<>();
            try (ResultSet rows = call.getResultSet()) {
                assertThat(rows.getMetaData().getColumnLabel(1)).isEqualTo("owner");
                while (rows.next()) {
                    balances.put(rows.getString("owner"), rows.getBigDecimal("balance"));
                }
            }

            assertThat(call.getMoreResults()).isTrue();
            List<String> transfers = new ArrayList<>();
            try (ResultSet rows = call.getResultSet()) {
                while (rows.next()) {
                    transfers.add(rows.getString("source") + "->" + rows.getString("target") + " "
                            + rows.getBigDecimal("amount").toPlainString() + " fee "
                            + rows.getBigDecimal("fee").toPlainString());
                }
            }

            assertThat(call.getMoreResults()).isTrue();
            BigDecimal totalHeld;
            try (ResultSet rows = call.getResultSet()) {
                assertThat(rows.next()).isTrue();
                totalHeld = rows.getBigDecimal("total_held");
            }
            return new StatementReport(balances, transfers, totalHeld);
        }
    }

    private static void createSchema() throws SQLException {
        try (Connection connection = pool.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE account (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        owner VARCHAR(32) NOT NULL UNIQUE,
                        balance DECIMAL(12, 2) NOT NULL
                    ) ENGINE = InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE transfer_log (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        source_id BIGINT NOT NULL,
                        target_id BIGINT NOT NULL,
                        amount DECIMAL(12, 2) NOT NULL,
                        fee DECIMAL(12, 2) NOT NULL
                    ) ENGINE = InnoDB
                    """);
            statement.execute("""
                    CREATE FUNCTION transfer_fee(amount DECIMAL(12, 2)) RETURNS DECIMAL(12, 2)
                    DETERMINISTIC NO SQL
                    RETURN GREATEST(ROUND(amount * 0.005, 2), 0.25)
                    """);
            statement.execute("""
                    CREATE PROCEDURE open_account(IN account_owner VARCHAR(32),
                                                  IN deposit DECIMAL(12, 2),
                                                  OUT account_id BIGINT)
                    BEGIN
                        INSERT INTO account (owner, balance) VALUES (account_owner, deposit);
                        SET account_id = LAST_INSERT_ID();
                    END
                    """);
            statement.execute("""
                    CREATE PROCEDURE transfer(IN source_id BIGINT, IN target_id BIGINT,
                                              IN amount DECIMAL(12, 2),
                                              OUT source_balance DECIMAL(12, 2))
                    BEGIN
                        DECLARE fee DECIMAL(12, 2) DEFAULT transfer_fee(amount);
                        DECLARE available DECIMAL(12, 2);
                        DECLARE failure VARCHAR(64);
                        START TRANSACTION;
                        SELECT balance INTO available FROM account WHERE id = source_id FOR UPDATE;
                        IF available < amount + fee THEN
                            ROLLBACK;
                            SET failure = CONCAT('insufficient funds in account ', source_id);
                            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = failure;
                        END IF;
                        UPDATE account SET balance = balance - amount - fee WHERE id = source_id;
                        UPDATE account SET balance = balance + amount WHERE id = target_id;
                        INSERT INTO transfer_log (source_id, target_id, amount, fee)
                            VALUES (source_id, target_id, amount, fee);
                        COMMIT;
                        SELECT balance INTO source_balance FROM account WHERE id = source_id;
                    END
                    """);
            statement.execute("""
                    CREATE PROCEDURE compound_interest(INOUT balance DECIMAL(12, 2), IN periods INT)
                    BEGIN
                        DECLARE remaining INT DEFAULT periods;
                        WHILE remaining > 0 DO
                            SET balance = ROUND(balance * 1.01, 2);
                            SET remaining = remaining - 1;
                        END WHILE;
                    END
                    """);
            statement.execute("""
                    CREATE PROCEDURE account_statement(IN minimum_balance DECIMAL(12, 2))
                    BEGIN
                        SELECT owner, balance FROM account
                            WHERE balance >= minimum_balance ORDER BY owner;
                        SELECT s.owner AS source, t.owner AS target, l.amount, l.fee
                            FROM transfer_log l
                            JOIN account s ON s.id = l.source_id
                            JOIN account t ON t.id = l.target_id
                            ORDER BY l.id;
                        SELECT SUM(balance) AS total_held FROM account;
                    END
                    """);
        }
    }

    /// Polls with a bounded deadline until the server accepts the application user.
    private static void awaitServer(String url, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        SQLException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection connection = DriverManager.getConnection(url, USER, PASSWORD);
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
