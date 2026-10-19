/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mysql.mysql_connector_j;

import com.mysql.cj.jdbc.MysqlDataSource;
import com.mysql.cj.jdbc.exceptions.MysqlDataTruncation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// JDBC transactions over prepared statements: a warehouse order service seeds stock with a
/// rewritten batch insert, reserves it under `SELECT ... FOR UPDATE`, reads generated order keys,
/// and rolls back orders that would oversell or violate a constraint.
public class WarehouseOrderTransactionTest {

    private static final String IMAGE = "container-registry.oracle.com/mysql/community-server:26.7.1";
    private static final String DATABASE = "warehouse";
    private static final String USER = "clerk";
    private static final String PASSWORD = "clerk-secret";
    private static final String CONTAINER = "real-use-mysql-orders-" + UUID.randomUUID();

    private static MysqlDataSource dataSource;

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
        dataSource = new MysqlDataSource();
        dataSource.setURL("jdbc:mysql://127.0.0.1:" + port + "/" + DATABASE
                + "?rewriteBatchedStatements=true&jdbcCompliantTruncation=true");
        dataSource.setUser(USER);
        dataSource.setPassword(PASSWORD);
        awaitServer(TimeUnit.MINUTES.toMillis(3));
        createSchema();
    }

    @AfterAll
    static void stopMysql() throws Exception {
        docker("rm", "-f", CONTAINER);
    }

    @Test
    void reservesStockAndRollsBackFailedOrders() throws SQLException {
        Map<String, Integer> delivery = new LinkedHashMap<>();
        delivery.put("BOLT-M6", 500);
        delivery.put("NUT-M6", 400);
        delivery.put("WASHER-M6", 50);
        int[] counts = receiveDelivery(delivery);
        assertThat(counts).hasSize(3);
        assertThat(stockLevels()).containsExactlyEntriesOf(delivery);

        long first = placeOrder("BOLT-M6", 120, "ACME Fasteners").orElseThrow();
        long second = placeOrder("NUT-M6", 400, "ACME Fasteners").orElseThrow();
        assertThat(second).isGreaterThan(first);

        // Overselling is detected under the row lock and leaves no trace.
        assertThat(placeOrder("WASHER-M6", 51, "Bolt & Co")).isEmpty();
        assertThat(placeOrder("NUT-M6", 1, "Bolt & Co")).isEmpty();

        // A customer name longer than the column is a truncation error, not a silent cut.
        assertThatThrownBy(() -> placeOrder("BOLT-M6", 10, "X".repeat(65)))
                .isInstanceOf(MysqlDataTruncation.class);

        // The same SKU delivered twice violates the primary key: the whole batch is undone.
        Map<String, Integer> duplicate = new LinkedHashMap<>();
        duplicate.put("SCREW-M4", 10);
        duplicate.put("BOLT-M6", 10);
        assertThatThrownBy(() -> receiveDelivery(duplicate))
                .isInstanceOfSatisfying(SQLException.class, e -> {
                    Throwable cause = e instanceof SQLIntegrityConstraintViolationException ? e : e.getCause();
                    assertThat(cause).isInstanceOf(SQLIntegrityConstraintViolationException.class);
                    SQLException violation = (SQLException) cause;
                    assertThat(violation.getErrorCode()).isEqualTo(1062);
                    assertThat(violation.getSQLState()).isEqualTo("23000");
                });

        Map<String, Integer> expectedStock = new LinkedHashMap<>();
        expectedStock.put("BOLT-M6", 380);
        expectedStock.put("NUT-M6", 0);
        expectedStock.put("WASHER-M6", 50);
        assertThat(stockLevels()).containsExactlyEntriesOf(expectedStock);
        assertThat(orderIds()).containsExactly(first, second);
    }

    /// Inserts every delivered SKU in one transaction as a single rewritten batch.
    private static int[] receiveDelivery(Map<String, Integer> delivery) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO stock (sku, on_hand) VALUES (?, ?)")) {
                for (Map.Entry<String, Integer> line : delivery.entrySet()) {
                    insert.setString(1, line.getKey());
                    insert.setInt(2, line.getValue());
                    insert.addBatch();
                }
                int[] counts = insert.executeBatch();
                connection.commit();
                return counts;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /// Reserves `quantity` of `sku` and records the order, returning its generated id, or
    /// nothing when the stock is too low.
    private static Optional<Long> placeOrder(String sku, int quantity, String customer) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int onHand;
                try (PreparedStatement lock = connection.prepareStatement(
                        "SELECT on_hand FROM stock WHERE sku = ? FOR UPDATE")) {
                    lock.setString(1, sku);
                    try (ResultSet row = lock.executeQuery()) {
                        assertThat(row.next()).isTrue();
                        onHand = row.getInt("on_hand");
                    }
                }
                if (onHand < quantity) {
                    connection.rollback();
                    return Optional.empty();
                }
                try (PreparedStatement reserve = connection.prepareStatement(
                        "UPDATE stock SET on_hand = on_hand - ? WHERE sku = ?")) {
                    reserve.setInt(1, quantity);
                    reserve.setString(2, sku);
                    assertThat(reserve.executeUpdate()).isEqualTo(1);
                }
                long orderId;
                try (PreparedStatement record = connection.prepareStatement(
                        "INSERT INTO orders (sku, quantity, customer) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    record.setString(1, sku);
                    record.setInt(2, quantity);
                    record.setString(3, customer);
                    record.executeUpdate();
                    try (ResultSet keys = record.getGeneratedKeys()) {
                        assertThat(keys.next()).isTrue();
                        orderId = keys.getLong(1);
                    }
                }
                connection.commit();
                return Optional.of(orderId);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private static Map<String, Integer> stockLevels() throws SQLException {
        Map<String, Integer> levels = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT sku, on_hand FROM stock ORDER BY sku")) {
            while (rows.next()) {
                levels.put(rows.getString("sku"), rows.getInt("on_hand"));
            }
        }
        return levels;
    }

    private static List<Long> orderIds() throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id FROM orders ORDER BY id")) {
            while (rows.next()) {
                ids.add(rows.getLong("id"));
            }
        }
        return ids;
    }

    private static void createSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE stock (
                        sku VARCHAR(32) PRIMARY KEY,
                        on_hand INT NOT NULL
                    ) ENGINE = InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE orders (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        sku VARCHAR(32) NOT NULL,
                        quantity INT NOT NULL,
                        customer VARCHAR(64) NOT NULL,
                        FOREIGN KEY (sku) REFERENCES stock (sku)
                    ) ENGINE = InnoDB
                    """);
        }
    }

    /// Polls with a bounded deadline until the server accepts the application user.
    private static void awaitServer(long timeoutMillis) throws InterruptedException {
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
