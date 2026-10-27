/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mariadb_jdbc.mariadb_java_client;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mariadb.jdbc.MariaDbDataSource;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Scrollable and updatable result sets: cursor navigation by absolute and relative
/// position, and in-place row updates, inserts, and deletes through `ResultSet`.
///
/// The scenario is a product catalog editor on top of `MariaDbDataSource` that pages
/// through a query result and edits the rows it is positioned on, the way a desktop
/// data-grid application does, against a real MariaDB server.
public class UpdatableCatalogCursorTest {

    private static final String IMAGE = "mariadb:13.0";

    private static final String CONTAINER = "real-use-mariadb-cursor-" + UUID.randomUUID();

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);

    private static final String DATABASE = "catalog";

    private static final String USER = "catalog_app";

    private static final String PASSWORD = "catalog-secret";

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

        dataSource = new MariaDbDataSource("jdbc:mariadb://127.0.0.1:" + port + "/" + DATABASE);
        dataSource.setUser(USER);
        dataSource.setPassword(PASSWORD);
        awaitServer();

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE products (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        sku VARCHAR(32) NOT NULL UNIQUE,
                        name VARCHAR(120) NOT NULL,
                        price DECIMAL(10, 2) NOT NULL,
                        discontinued_on DATE NULL
                    ) ENGINE=InnoDB""");
        }
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @BeforeEach
    void seedCatalog() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM products");
            statement.execute("""
                    INSERT INTO products (sku, name, price, discontinued_on) VALUES
                        ('A-100', 'Anvil', 120.00, NULL),
                        ('B-200', 'Bucket', 8.50, NULL),
                        ('C-300', 'Chisel', 14.25, NULL),
                        ('D-400', 'Drill', 89.99, NULL),
                        ('E-500', 'Easel', 45.00, NULL)""");
        }
    }

    @Test
    void navigatesTheCursorInEveryDirection() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement(
                        ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                ResultSet rows = statement.executeQuery("SELECT sku, name FROM products ORDER BY sku")) {
            assertThat(rows.getType()).isEqualTo(ResultSet.TYPE_SCROLL_INSENSITIVE);
            assertThat(rows.isBeforeFirst()).isTrue();

            assertThat(rows.last()).isTrue();
            assertThat(rows.getRow()).isEqualTo(5);
            assertThat(rows.getString("sku")).isEqualTo("E-500");

            assertThat(rows.absolute(2)).isTrue();
            assertThat(rows.getString("name")).isEqualTo("Bucket");
            assertThat(rows.relative(2)).isTrue();
            assertThat(rows.getString("name")).isEqualTo("Drill");
            assertThat(rows.previous()).isTrue();
            assertThat(rows.getString("name")).isEqualTo("Chisel");
            assertThat(rows.absolute(-1)).isTrue();
            assertThat(rows.isLast()).isTrue();

            assertThat(rows.absolute(9)).isFalse();
            assertThat(rows.isAfterLast()).isTrue();

            rows.beforeFirst();
            List<String> forward = new ArrayList<>();
            while (rows.next()) {
                forward.add(rows.getString("sku"));
            }
            List<String> backward = new ArrayList<>();
            rows.afterLast();
            while (rows.previous()) {
                backward.add(rows.getString("sku"));
            }
            assertThat(forward).containsExactly("A-100", "B-200", "C-300", "D-400", "E-500");
            assertThat(backward).containsExactly("E-500", "D-400", "C-300", "B-200", "A-100");
        }
    }

    @Test
    void editsInsertsAndDeletesRowsThroughTheCursor() throws SQLException {
        CatalogEditor editor = new CatalogEditor(dataSource);

        editor.edit(rows -> {
            assertThat(rows.getConcurrency()).isEqualTo(ResultSet.CONCUR_UPDATABLE);

            assertThat(rows.absolute(3)).isTrue();
            assertThat(rows.getString("sku")).isEqualTo("C-300");
            rows.updateBigDecimal("price", new BigDecimal("15.75"));
            rows.updateString("name", "Wood chisel");
            rows.updateRow();
            assertThat(rows.getBigDecimal("price")).isEqualByComparingTo("15.75");

            assertThat(rows.first()).isTrue();
            rows.updateObject("discontinued_on", LocalDate.of(2025, 2, 28));
            rows.updateRow();

            assertThat(rows.absolute(2)).isTrue();
            assertThat(rows.getString("sku")).isEqualTo("B-200");
            rows.deleteRow();

            rows.moveToInsertRow();
            rows.updateString("sku", "F-600");
            rows.updateString("name", "File");
            rows.updateBigDecimal("price", new BigDecimal("6.40"));
            rows.updateNull("discontinued_on");
            rows.insertRow();
            rows.moveToCurrentRow();
        });

        Map<String, Product> catalog = editor.catalog();
        assertThat(catalog.keySet()).containsExactly("A-100", "C-300", "D-400", "E-500", "F-600");
        assertThat(catalog.get("C-300")).isEqualTo(
                new Product("C-300", "Wood chisel", new BigDecimal("15.75"), null));
        assertThat(catalog.get("A-100").discontinuedOn()).isEqualTo(LocalDate.of(2025, 2, 28));
        assertThat(catalog.get("F-600")).isEqualTo(new Product("F-600", "File", new BigDecimal("6.40"), null));
        assertThat(catalog.get("D-400")).isEqualTo(new Product("D-400", "Drill", new BigDecimal("89.99"), null));
    }

    @Test
    void cancelledEditLeavesTheRowUntouched() throws SQLException {
        CatalogEditor editor = new CatalogEditor(dataSource);

        editor.edit(rows -> {
            assertThat(rows.last()).isTrue();
            rows.updateString("name", "Folding easel");
            rows.cancelRowUpdates();
            assertThat(rows.getString("name")).isEqualTo("Easel");

            rows.updateBigDecimal("price", new BigDecimal("39.00"));
            rows.updateRow();
            rows.refreshRow();
            assertThat(rows.getBigDecimal("price")).isEqualByComparingTo("39.00");
            assertThat(rows.getString("name")).isEqualTo("Easel");
        });

        assertThat(editor.catalog().get("E-500")).isEqualTo(
                new Product("E-500", "Easel", new BigDecimal("39.00"), null));
    }

    @Test
    void joinedQueryIsReadOnlyEvenWhenUpdatableIsRequested() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement(
                        ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
                ResultSet rows = statement.executeQuery("""
                        SELECT p.sku, q.sku AS other
                        FROM products p JOIN products q ON q.id = p.id
                        ORDER BY p.sku""")) {
            assertThat(rows.next()).isTrue();
            assertThatThrownBy(() -> {
                rows.updateString("sku", "Z-999");
                rows.updateRow();
            }).isInstanceOf(SQLException.class);
        }
        assertThat(new CatalogEditor(dataSource).catalog()).containsKey("A-100").doesNotContainKey("Z-999");
    }

    /// A catalog entry as the application models it.
    record Product(String sku, String name, BigDecimal price, LocalDate discontinuedOn) {
    }

    /// One editing session over the cursor, as a grid view would perform it.
    @FunctionalInterface
    interface CursorEdit {

        void apply(ResultSet rows) throws SQLException;
    }

    /// The application's catalog editor, which edits rows through an updatable cursor.
    static final class CatalogEditor {

        private final MariaDbDataSource dataSource;

        CatalogEditor(MariaDbDataSource dataSource) {
            this.dataSource = dataSource;
        }

        void edit(CursorEdit edit) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement query = connection.prepareStatement(
                            "SELECT id, sku, name, price, discontinued_on FROM products ORDER BY sku",
                            ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
                    ResultSet rows = query.executeQuery()) {
                edit.apply(rows);
            }
        }

        Map<String, Product> catalog() throws SQLException {
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery(
                            "SELECT sku, name, price, discontinued_on FROM products ORDER BY sku")) {
                Map<String, Product> catalog = new LinkedHashMap<>();
                while (rows.next()) {
                    String sku = rows.getString("sku");
                    catalog.put(sku, new Product(sku, rows.getString("name"), rows.getBigDecimal("price"),
                            rows.getObject("discontinued_on", LocalDate.class)));
                }
                return catalog;
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
