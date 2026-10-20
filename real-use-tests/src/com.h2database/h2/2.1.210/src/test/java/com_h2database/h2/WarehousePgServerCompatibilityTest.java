/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_h2database.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.h2.tools.Server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The PostgreSQL server mode: a PG wire-protocol server started with
 * `org.h2.tools.Server.createPgServer`, reached by the PostgreSQL JDBC driver directly and through
 * a HikariCP pool, with prepared statements, typed parameters, transactions, server-side SQL
 * states, and the same database file read back through an embedded H2 connection.
 *
 * A warehouse keeps its stock database in H2 but its reporting tools only speak PostgreSQL: they
 * connect to the H2 PG server with the stock PostgreSQL driver, record shipments in pooled
 * transactions, and the warehouse service itself reads the same file through embedded H2.
 */
public class WarehousePgServerCompatibilityTest {

    private static final String USER = "clerk";

    private static final String PASSWORD = "forklift";

    /// The settings the PG server applies to its sessions, so embedded sessions see the same names.
    private static final String PG_SETTINGS = ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";

    private static final String SCHEMA = """
            CREATE TABLE stock (
                sku VARCHAR(20) PRIMARY KEY,
                title VARCHAR(80) NOT NULL,
                quantity INT NOT NULL,
                unit_price NUMERIC(10, 2) NOT NULL,
                restocked_on DATE NOT NULL,
                fragile BOOLEAN NOT NULL
            )
            """;

    @TempDir
    Path baseDir;

    @Test
    void reportingToolsTalkToH2OverThePostgresWireProtocol() throws SQLException {
        Server server = startServer();
        try {
            assertTrue(server.isRunning(true));
            assertTrue(server.getURL().startsWith("pg://"));
            String url = "jdbc:postgresql://localhost:" + server.getPort() + "/warehouse";
            try (Connection connection = DriverManager.getConnection(url, USER, PASSWORD);
                    Statement statement = connection.createStatement()) {
                assertEquals("org.postgresql.Driver", DriverManager.getDriver(url).getClass().getName());
                assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
                statement.execute(SCHEMA);
                assertEquals(1, addStock(connection, "BOX-S", "Small box", 120, "1.25",
                        LocalDate.of(2026, 3, 1), false));
                assertEquals(1, addStock(connection, "VASE-G", "Glass vase", 8, "34.90",
                        LocalDate.of(2026, 3, 4), true));
                assertEquals(1, addStock(connection, "TAPE-W", "Wide tape", 60, "3.10",
                        LocalDate.of(2026, 2, 27), false));

                try (PreparedStatement fragileQuery = connection.prepareStatement("""
                        SELECT sku, title, quantity, unit_price, restocked_on
                        FROM stock WHERE fragile = ? ORDER BY sku
                        """)) {
                    fragileQuery.setBoolean(1, true);
                    try (ResultSet rows = fragileQuery.executeQuery()) {
                        assertTrue(rows.next());
                        assertEquals("VASE-G", rows.getString("sku"));
                        assertEquals("Glass vase", rows.getString("title"));
                        assertEquals(8, rows.getInt("quantity"));
                        assertEquals(0, new BigDecimal("34.90").compareTo(rows.getBigDecimal("unit_price")));
                        assertEquals(LocalDate.of(2026, 3, 4), rows.getDate("restocked_on").toLocalDate());
                        assertFalse(rows.next());
                    }
                }

                try (ResultSet totals = statement.executeQuery("""
                        SELECT COUNT(*), SUM(quantity), SUM(quantity * unit_price) FROM stock
                        """)) {
                    assertTrue(totals.next());
                    assertEquals(3, totals.getInt(1));
                    assertEquals(188, totals.getInt(2));
                    assertEquals(0, new BigDecimal("615.20").compareTo(totals.getBigDecimal(3)));
                }

                SQLException duplicate = assertThrows(SQLException.class, () -> addStock(connection,
                        "BOX-S", "Duplicate box", 1, "1.00", LocalDate.of(2026, 3, 5), false));
                assertEquals("23505", duplicate.getSQLState());
            }
        } finally {
            server.stop();
        }
        assertFalse(server.isRunning(false));

        try (Connection embedded = DriverManager.getConnection(embeddedUrl(), USER, PASSWORD)) {
            assertEquals(List.of("BOX-S:120", "TAPE-W:60", "VASE-G:8"), stockLevels(embedded));
        }
    }

    @Test
    void pooledShipmentsCommitOrRollBackAsOneTransaction() throws SQLException {
        Server server = startServer();
        try (HikariDataSource pool = createPool(server.getPort())) {
            try (Connection connection = pool.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute(SCHEMA);
                addStock(connection, "BOX-S", "Small box", 120, "1.25", LocalDate.of(2026, 3, 1), false);
                addStock(connection, "VASE-G", "Glass vase", 8, "34.90", LocalDate.of(2026, 3, 4), true);
            }

            try (Connection connection = pool.getConnection()) {
                connection.setAutoCommit(false);
                assertEquals(1, ship(connection, "BOX-S", 20));
                assertEquals(1, ship(connection, "VASE-G", 3));
                connection.commit();
                connection.setAutoCommit(true);
            }

            try (Connection connection = pool.getConnection()) {
                connection.setAutoCommit(false);
                assertEquals(1, ship(connection, "BOX-S", 100));
                assertEquals(List.of("BOX-S:0", "VASE-G:5"), stockLevels(connection));
                connection.rollback();
                connection.setAutoCommit(true);
            }

            try (Connection connection = pool.getConnection()) {
                assertTrue(connection.isValid(5));
                assertEquals(List.of("BOX-S:100", "VASE-G:5"), stockLevels(connection));
                SQLException missing = assertThrows(SQLException.class, () -> {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("SELECT * FROM missing_table");
                    }
                });
                assertEquals("42S02", missing.getSQLState());
            }
            assertEquals(0, pool.getHikariPoolMXBean().getActiveConnections());
        } finally {
            server.stop();
        }

        try (Connection embedded = DriverManager.getConnection(embeddedUrl(), USER, PASSWORD)) {
            assertEquals(List.of("BOX-S:100", "VASE-G:5"), stockLevels(embedded));
        }
    }

    private Server startServer() throws SQLException {
        try (Connection owner = DriverManager.getConnection(embeddedUrl(), USER, PASSWORD)) {
            assertTrue(owner.isValid(1));
        }
        return Server.createPgServer("-pgPort", "0", "-baseDir", baseDir.toString()).start();
    }

    private String embeddedUrl() {
        return "jdbc:h2:" + baseDir.resolve("warehouse") + PG_SETTINGS;
    }

    private static HikariDataSource createPool(int port) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:" + port + "/warehouse");
        config.setUsername(USER);
        config.setPassword(PASSWORD);
        config.setMaximumPoolSize(2);
        config.setPoolName("warehouse-reporting");
        return new HikariDataSource(config);
    }

    private static int addStock(Connection connection, String sku, String title, int quantity,
            String unitPrice, LocalDate restockedOn, boolean fragile) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO stock (sku, title, quantity, unit_price, restocked_on, fragile)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            insert.setString(1, sku);
            insert.setString(2, title);
            insert.setInt(3, quantity);
            insert.setBigDecimal(4, new BigDecimal(unitPrice));
            insert.setDate(5, Date.valueOf(restockedOn));
            insert.setBoolean(6, fragile);
            return insert.executeUpdate();
        }
    }

    private static int ship(Connection connection, String sku, int quantity) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE stock SET quantity = quantity - ? WHERE sku = ? AND quantity >= ?")) {
            update.setInt(1, quantity);
            update.setString(2, sku);
            update.setInt(3, quantity);
            return update.executeUpdate();
        }
    }

    private static List<String> stockLevels(Connection connection) throws SQLException {
        List<String> levels = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT sku, quantity FROM stock ORDER BY sku")) {
            while (rows.next()) {
                levels.add(rows.getString(1) + ":" + rows.getInt(2));
            }
        }
        return levels;
    }
}
