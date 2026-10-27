/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mariadb_jdbc.mariadb_java_client;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mariadb.jdbc.MariaDbDataSource;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Transactional writes through server-side prepared statements: batched inserts,
/// generated keys, savepoints, and rollback after a constraint violation.
///
/// The scenario is an order-entry repository on top of `MariaDbDataSource` that
/// places orders with their lines in one transaction against a real MariaDB server.
public class TransactionalOrderWritesTest {

    private static final String IMAGE = "mariadb:13.0";

    private static final String CONTAINER = "real-use-mariadb-orders-" + UUID.randomUUID();

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);

    private static final String DATABASE = "shop";

    private static final String USER = "shop_app";

    private static final String PASSWORD = "shop-secret";

    private static MariaDbDataSource dataSource;

    @BeforeAll
    static void startServer() throws Exception {
        docker("run", "-d", "--name", CONTAINER, "-p", "127.0.0.1::3306",
                "-e", "MARIADB_DATABASE=" + DATABASE,
                "-e", "MARIADB_USER=" + USER,
                "-e", "MARIADB_PASSWORD=" + PASSWORD,
                "-e", "MARIADB_ALLOW_EMPTY_ROOT_PASSWORD=true",
                IMAGE);
        String mapping = docker("port", CONTAINER, "3306/tcp").lines().findFirst().orElseThrow();
        int port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));

        dataSource = new MariaDbDataSource(
                "jdbc:mariadb://127.0.0.1:" + port + "/" + DATABASE + "?useServerPrepStmts=true");
        dataSource.setUser(USER);
        dataSource.setPassword(PASSWORD);
        awaitServer();

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE customers (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        email VARCHAR(120) NOT NULL UNIQUE
                    ) ENGINE=InnoDB""");
            statement.execute("""
                    CREATE TABLE orders (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        customer_id INT NOT NULL,
                        placed_at DATETIME NOT NULL,
                        FOREIGN KEY (customer_id) REFERENCES customers (id)
                    ) ENGINE=InnoDB""");
            statement.execute("""
                    CREATE TABLE order_lines (
                        order_id BIGINT NOT NULL,
                        sku VARCHAR(32) NOT NULL,
                        quantity INT NOT NULL,
                        unit_price DECIMAL(10, 2) NOT NULL,
                        PRIMARY KEY (order_id, sku),
                        FOREIGN KEY (order_id) REFERENCES orders (id)
                    ) ENGINE=InnoDB""");
        }
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @Test
    void placesOrderWithBatchedLinesInOneTransaction() throws Exception {
        OrderRepository repository = new OrderRepository(dataSource);
        int customerId = repository.registerCustomer("ada@example.org");
        LocalDateTime placedAt = LocalDateTime.of(2025, 3, 14, 9, 26, 53);

        long orderId = repository.placeOrder(customerId, placedAt, List.of(
                new OrderLine("BOOK-001", 2, new BigDecimal("19.90")),
                new OrderLine("PEN-042", 10, new BigDecimal("1.25")),
                new OrderLine("LAMP-7", 1, new BigDecimal("34.00"))));

        assertThat(orderId).isPositive();
        assertThat(repository.orderTotal(orderId)).isEqualByComparingTo("86.30");
        assertThat(repository.placedAt(orderId)).isEqualTo(placedAt);
        assertThat(repository.skus(orderId)).containsExactly("BOOK-001", "LAMP-7", "PEN-042");

        long secondOrder = repository.placeOrder(customerId, placedAt.plusDays(1), List.of(
                new OrderLine("BOOK-001", 1, new BigDecimal("19.90"))));
        assertThat(secondOrder).isGreaterThan(orderId);
        assertThat(repository.orderCount(customerId)).isEqualTo(2);
    }

    @Test
    void constraintViolationRollsBackTheWholeOrder() throws Exception {
        OrderRepository repository = new OrderRepository(dataSource);
        int customerId = repository.registerCustomer("grace@example.org");

        assertThatThrownBy(() -> repository.placeOrder(customerId, LocalDateTime.of(2025, 1, 2, 3, 4, 5), List.of(
                new OrderLine("DUP-1", 1, new BigDecimal("5.00")),
                new OrderLine("DUP-1", 3, new BigDecimal("5.00")))))
                .isInstanceOf(SQLException.class);
        assertThat(repository.orderCount(customerId)).isZero();

        assertThatThrownBy(() -> repository.registerCustomer("grace@example.org"))
                .isInstanceOf(SQLIntegrityConstraintViolationException.class)
                .hasMessageContaining("Duplicate entry");
        assertThatThrownBy(() -> repository.placeOrder(999_999, LocalDateTime.of(2025, 1, 2, 3, 4, 5), List.of()))
                .isInstanceOf(SQLIntegrityConstraintViolationException.class);
    }

    @Test
    void savepointKeepsOrderButDiscardsRejectedLines() throws Exception {
        OrderRepository repository = new OrderRepository(dataSource);
        int customerId = repository.registerCustomer("linus@example.org");

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            long orderId = repository.insertOrder(connection, customerId, LocalDateTime.of(2025, 6, 1, 12, 0));
            repository.insertLines(connection, orderId, List.of(new OrderLine("KEEP-1", 4, new BigDecimal("2.50"))));

            Savepoint beforeExtras = connection.setSavepoint("extras");
            repository.insertLines(connection, orderId, List.of(new OrderLine("EXTRA-1", 1, new BigDecimal("99.99"))));
            assertThat(repository.skus(connection, orderId)).containsExactly("EXTRA-1", "KEEP-1");
            connection.rollback(beforeExtras);
            connection.commit();

            assertThat(repository.skus(orderId)).containsExactly("KEEP-1");
            assertThat(repository.orderTotal(orderId)).isEqualByComparingTo("10.00");
        }
    }

    /// One line of an order as the application models it.
    record OrderLine(String sku, int quantity, BigDecimal unitPrice) {
    }

    /// The application's data-access layer for customers and orders.
    static final class OrderRepository {

        private final MariaDbDataSource dataSource;

        OrderRepository(MariaDbDataSource dataSource) {
            this.dataSource = dataSource;
        }

        int registerCustomer(String email) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO customers (email) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, email);
                assertThat(insert.executeUpdate()).isEqualTo(1);
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    assertThat(keys.next()).isTrue();
                    return keys.getInt(1);
                }
            }
        }

        long placeOrder(int customerId, LocalDateTime placedAt, List<OrderLine> lines) throws SQLException {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                try {
                    long orderId = insertOrder(connection, customerId, placedAt);
                    insertLines(connection, orderId, lines);
                    connection.commit();
                    return orderId;
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                }
            }
        }

        long insertOrder(Connection connection, int customerId, LocalDateTime placedAt) throws SQLException {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO orders (customer_id, placed_at) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                insert.setInt(1, customerId);
                insert.setObject(2, placedAt);
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    assertThat(keys.next()).isTrue();
                    return keys.getLong(1);
                }
            }
        }

        void insertLines(Connection connection, long orderId, List<OrderLine> lines) throws SQLException {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO order_lines (order_id, sku, quantity, unit_price) VALUES (?, ?, ?, ?)")) {
                for (OrderLine line : lines) {
                    insert.setLong(1, orderId);
                    insert.setString(2, line.sku());
                    insert.setInt(3, line.quantity());
                    insert.setBigDecimal(4, line.unitPrice());
                    insert.addBatch();
                }
                int[] counts = insert.executeBatch();
                assertThat(counts).hasSize(lines.size());
            }
        }

        BigDecimal orderTotal(long orderId) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement query = connection.prepareStatement(
                            "SELECT SUM(quantity * unit_price) FROM order_lines WHERE order_id = ?")) {
                query.setLong(1, orderId);
                try (ResultSet rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    return rows.getBigDecimal(1);
                }
            }
        }

        LocalDateTime placedAt(long orderId) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement query = connection.prepareStatement(
                            "SELECT placed_at FROM orders WHERE id = ?")) {
                query.setLong(1, orderId);
                try (ResultSet rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    return rows.getObject("placed_at", LocalDateTime.class);
                }
            }
        }

        int orderCount(int customerId) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement query = connection.prepareStatement(
                            "SELECT COUNT(*) FROM orders WHERE customer_id = ?")) {
                query.setInt(1, customerId);
                try (ResultSet rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    return rows.getInt(1);
                }
            }
        }

        List<String> skus(long orderId) throws SQLException {
            try (Connection connection = dataSource.getConnection()) {
                return skus(connection, orderId);
            }
        }

        List<String> skus(Connection connection, long orderId) throws SQLException {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT sku FROM order_lines WHERE order_id = ? ORDER BY sku")) {
                query.setLong(1, orderId);
                List<String> skus = new ArrayList<>();
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        skus.add(rows.getString("sku"));
                    }
                }
                return skus;
            }
        }
    }

    private static void awaitServer() throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        SQLException last = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = dataSource.getConnection()) {
                if (connection.isValid(5)) {
                    return;
                }
            } catch (SQLException e) {
                last = e;
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("MariaDB did not accept connections in time", last);
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
