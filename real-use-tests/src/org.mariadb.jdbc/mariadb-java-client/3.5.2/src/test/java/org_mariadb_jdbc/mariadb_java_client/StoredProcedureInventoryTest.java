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

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Stored procedures and stored functions called through `CallableStatement`:
/// IN, OUT and INOUT parameters, named parameters, function return values,
/// procedures that return several result sets, and errors raised with `SIGNAL`.
///
/// The scenario is an inventory service that keeps its business rules in
/// server-side routines and reaches the driver through `DriverManager`, the way
/// an application without a pool or `DataSource` bootstraps it.
public class StoredProcedureInventoryTest {

    private static final String IMAGE = "mariadb:13.0";

    private static final String CONTAINER = "real-use-mariadb-routines-" + UUID.randomUUID();

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);

    private static final String DATABASE = "warehouse";

    private static final String USER = "warehouse_app";

    private static final String PASSWORD = "warehouse-secret";

    private static String url;

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
        url = "jdbc:mariadb://127.0.0.1:" + port + "/" + DATABASE;
        awaitServer();

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE stock (
                        sku VARCHAR(32) PRIMARY KEY,
                        on_hand INT NOT NULL,
                        unit_price DECIMAL(10, 2) NOT NULL
                    ) ENGINE=InnoDB""");
            statement.execute("""
                    INSERT INTO stock (sku, on_hand, unit_price) VALUES
                        ('BOLT-M6', 500, 0.12),
                        ('NUT-M6', 40, 0.08),
                        ('WASHER-M6', 3, 0.05),
                        ('DRILL-10', 7, 89.00)""");
            statement.execute("""
                    CREATE PROCEDURE reserve_stock(
                        IN p_sku VARCHAR(32), IN p_quantity INT, OUT p_remaining INT)
                    BEGIN
                        DECLARE available INT;
                        SELECT on_hand INTO available FROM stock WHERE sku = p_sku FOR UPDATE;
                        IF available IS NULL THEN
                            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unknown SKU';
                        END IF;
                        IF available < p_quantity THEN
                            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Insufficient stock';
                        END IF;
                        UPDATE stock SET on_hand = on_hand - p_quantity WHERE sku = p_sku;
                        SET p_remaining = available - p_quantity;
                    END""");
            statement.execute("""
                    CREATE PROCEDURE apply_discount(INOUT p_price DECIMAL(10, 2), IN p_percent INT)
                    BEGIN
                        SET p_price = ROUND(p_price * (100 - p_percent) / 100, 2);
                    END""");
            statement.execute("""
                    CREATE FUNCTION stock_value(p_sku VARCHAR(32)) RETURNS DECIMAL(12, 2)
                    READS SQL DATA
                    BEGIN
                        DECLARE result DECIMAL(12, 2);
                        SELECT on_hand * unit_price INTO result FROM stock WHERE sku = p_sku;
                        RETURN result;
                    END""");
            statement.execute("""
                    CREATE PROCEDURE stock_report(IN p_threshold INT)
                    BEGIN
                        SELECT sku, on_hand FROM stock WHERE on_hand < p_threshold ORDER BY sku;
                        SELECT COUNT(*) AS skus, SUM(on_hand) AS units FROM stock;
                    END""");
        }
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @Test
    void reservesStockThroughOutParameter() throws Exception {
        InventoryService inventory = new InventoryService();

        assertThat(inventory.reserve("BOLT-M6", 120)).isEqualTo(380);
        assertThat(inventory.reserve("BOLT-M6", 80)).isEqualTo(300);
        assertThat(inventory.onHand("BOLT-M6")).isEqualTo(300);
    }

    @Test
    void signalledBusinessErrorsSurfaceAsSqlExceptions() throws Exception {
        InventoryService inventory = new InventoryService();

        assertThatThrownBy(() -> inventory.reserve("DRILL-10", 8))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("Insufficient stock")
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("45000"));
        assertThatThrownBy(() -> inventory.reserve("SAW-1", 1))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("Unknown SKU");
        assertThat(inventory.onHand("DRILL-10")).isEqualTo(7);
    }

    @Test
    void namedInOutParameterCarriesValueBothWays() throws Exception {
        InventoryService inventory = new InventoryService();

        assertThat(inventory.discounted(new BigDecimal("89.00"), 15)).isEqualByComparingTo("75.65");
        assertThat(inventory.discounted(new BigDecimal("0.12"), 50)).isEqualByComparingTo("0.06");
    }

    @Test
    void storedFunctionReturnsValueThroughEscapeSyntax() throws Exception {
        InventoryService inventory = new InventoryService();

        assertThat(inventory.stockValue("DRILL-10")).isEqualByComparingTo("623.00");
        assertThat(inventory.stockValue("WASHER-M6")).isEqualByComparingTo("0.15");
        assertThat(inventory.stockValue("SAW-1")).isNull();
    }

    @Test
    void procedureReturnsSeveralResultSets() throws Exception {
        InventoryService inventory = new InventoryService();

        StockReport report = inventory.report(10);

        assertThat(report.lowStock()).containsExactly(Map.entry("DRILL-10", 7), Map.entry("WASHER-M6", 3));
        assertThat(report.skuCount()).isEqualTo(4);
        assertThat(report.units()).isPositive();
    }

    /// What the stock report procedure tells the application.
    record StockReport(Map<String, Integer> lowStock, int skuCount, long units) {
    }

    /// The application's inventory service, delegating its rules to stored routines.
    static final class InventoryService {

        int reserve(String sku, int quantity) throws SQLException {
            try (Connection connection = connect();
                    CallableStatement call = connection.prepareCall("{call reserve_stock(?, ?, ?)}")) {
                call.setString(1, sku);
                call.setInt(2, quantity);
                call.registerOutParameter(3, Types.INTEGER);
                call.execute();
                return call.getInt(3);
            }
        }

        BigDecimal discounted(BigDecimal price, int percent) throws SQLException {
            try (Connection connection = connect();
                    CallableStatement call = connection.prepareCall("{call apply_discount(?, ?)}")) {
                call.registerOutParameter("p_price", Types.DECIMAL);
                call.setBigDecimal("p_price", price);
                call.setInt("p_percent", percent);
                call.execute();
                return call.getBigDecimal("p_price");
            }
        }

        BigDecimal stockValue(String sku) throws SQLException {
            try (Connection connection = connect();
                    CallableStatement call = connection.prepareCall("{? = call stock_value(?)}")) {
                call.registerOutParameter(1, Types.DECIMAL);
                call.setString(2, sku);
                call.execute();
                return call.getBigDecimal(1);
            }
        }

        StockReport report(int threshold) throws SQLException {
            try (Connection connection = connect();
                    CallableStatement call = connection.prepareCall("{call stock_report(?)}")) {
                call.setInt(1, threshold);
                assertThat(call.execute()).isTrue();
                Map<String, Integer> lowStock = new LinkedHashMap<>();
                try (ResultSet rows = call.getResultSet()) {
                    while (rows.next()) {
                        lowStock.put(rows.getString("sku"), rows.getInt("on_hand"));
                    }
                }
                assertThat(call.getMoreResults()).isTrue();
                try (ResultSet totals = call.getResultSet()) {
                    assertThat(totals.next()).isTrue();
                    return new StockReport(lowStock, totals.getInt("skus"), totals.getLong("units"));
                }
            }
        }

        int onHand(String sku) throws SQLException {
            try (Connection connection = connect();
                    PreparedStatement query = connection.prepareStatement("SELECT on_hand FROM stock WHERE sku = ?")) {
                query.setString(1, sku);
                try (ResultSet rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    return rows.getInt(1);
                }
            }
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(url, USER, PASSWORD);
    }

    private static void awaitServer() throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        SQLException last = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = connect()) {
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
