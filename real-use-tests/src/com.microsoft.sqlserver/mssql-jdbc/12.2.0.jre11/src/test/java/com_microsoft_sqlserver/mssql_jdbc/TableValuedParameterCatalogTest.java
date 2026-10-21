/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_microsoft_sqlserver.mssql_jdbc;

import com.microsoft.sqlserver.jdbc.ISQLServerDataRecord;
import com.microsoft.sqlserver.jdbc.SQLServerCallableStatement;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.microsoft.sqlserver.jdbc.SQLServerDataTable;
import com.microsoft.sqlserver.jdbc.SQLServerException;
import com.microsoft.sqlserver.jdbc.SQLServerMetaData;
import com.microsoft.sqlserver.jdbc.SQLServerPreparedStatement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Sends whole row sets to SQL Server as table-valued parameters built from
/// `SQLServerDataTable`, streamed `ISQLServerDataRecord` sources, and `ResultSet` sources.
///
/// The scenario is a product catalog service that looks up products by a list
/// of SKUs, imports order lines in one round trip, applies price changes
/// through a procedure that takes a table type, and copies a staging table
/// into the catalog, against a real SQL Server.
public class TableValuedParameterCatalogTest {

    private static final String IMAGE = "mcr.microsoft.com/mssql/server:2022-CU14-ubuntu-22.04";

    private static final String PASSWORD = "CatalogTvp#2024";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(180);

    private static final String CONTAINER = "real-use-mssql-tvp-" + UUID.randomUUID();

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
        dataSource.setApplicationName("catalog-service");
        dataSource.setLoginTimeout(5);
        awaitServer();
        createSchema();
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @Test
    void skuListParameterSelectsMatchingProducts() throws Exception {
        SQLServerDataTable skus = new SQLServerDataTable();
        skus.addColumnMetadata("sku", Types.NVARCHAR);
        skus.addRow("SKU-MOUSE");
        skus.addRow("SKU-DESK");
        skus.addRow("SKU-MISSING");

        Map<String, String> found = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement lookup = connection.prepareStatement("""
                        SELECT p.sku, p.name
                        FROM dbo.products p
                        JOIN ? AS wanted ON wanted.sku = p.sku
                        ORDER BY p.sku
                        """)) {
            lookup.unwrap(SQLServerPreparedStatement.class).setStructured(1, "dbo.SkuList", skus);
            try (ResultSet rows = lookup.executeQuery()) {
                while (rows.next()) {
                    found.put(rows.getString("sku"), rows.getString("name"));
                }
            }
        }

        assertThat(found).containsExactly(Map.entry("SKU-DESK", "Standing desk"), Map.entry("SKU-MOUSE", "Wireless mouse"));
    }

    @Test
    void streamedOrderLinesAreInsertedInOneStatement() throws Exception {
        List<OrderLine> lines = List.of(
                new OrderLine("SKU-MOUSE", 2, new BigDecimal("24.50"), null),
                new OrderLine("SKU-DESK", 1, new BigDecimal("349.00"), "Deliver to floor 3"),
                new OrderLine("SKU-LAMP", 4, new BigDecimal("18.75"), null));

        int inserted;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO dbo.order_lines (order_id, sku, quantity, unit_price, note)
                        SELECT ?, sku, quantity, unit_price, note FROM ?
                        """)) {
            SQLServerPreparedStatement sqlServerInsert = insert.unwrap(SQLServerPreparedStatement.class);
            sqlServerInsert.setInt(1, 1001);
            sqlServerInsert.setStructured(2, "dbo.OrderLineType", new OrderLineRecords(lines));
            inserted = sqlServerInsert.executeUpdate();
        }

        assertThat(inserted).isEqualTo(3);
        try (Connection connection = dataSource.getConnection();
                PreparedStatement totals = connection.prepareStatement("""
                        SELECT COUNT(*), SUM(quantity * unit_price), MAX(note)
                        FROM dbo.order_lines WHERE order_id = ?
                        """)) {
            totals.setInt(1, 1001);
            try (ResultSet row = totals.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getInt(1)).isEqualTo(3);
                assertThat(row.getBigDecimal(2)).isEqualByComparingTo(new BigDecimal("473.00"));
                assertThat(row.getString(3)).isEqualTo("Deliver to floor 3");
            }
        }
    }

    @Test
    void priceChangeProcedureReceivesTableTypeAndReportsChangedRows() throws Exception {
        SQLServerDataTable changes = new SQLServerDataTable();
        changes.addColumnMetadata("sku", Types.NVARCHAR);
        changes.addColumnMetadata("new_price", Types.DECIMAL);
        changes.addRow("SKU-MOUSE", new BigDecimal("22.99"));
        changes.addRow("SKU-LAMP", new BigDecimal("15.00"));
        changes.addRow("SKU-GHOST", new BigDecimal("1.00"));

        try (Connection connection = dataSource.getConnection();
                CallableStatement apply = connection.prepareCall("{call dbo.apply_price_changes(?, ?)}")) {
            SQLServerCallableStatement sqlServerApply = apply.unwrap(SQLServerCallableStatement.class);
            sqlServerApply.setStructured(1, "dbo.PriceChangeType", changes);
            sqlServerApply.registerOutParameter(2, Types.INTEGER);
            sqlServerApply.execute();

            assertThat(sqlServerApply.getInt(2)).isEqualTo(2);
        }

        assertThat(priceOf("SKU-MOUSE")).isEqualByComparingTo(new BigDecimal("22.99"));
        assertThat(priceOf("SKU-LAMP")).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(priceOf("SKU-DESK")).isEqualByComparingTo(new BigDecimal("349.00"));
    }

    @Test
    void stagingResultSetIsCopiedIntoTheCatalog() throws Exception {
        int copied;
        try (Connection source = dataSource.getConnection();
                Statement staging = source.createStatement();
                ResultSet stagedProducts = staging.executeQuery("""
                        SELECT sku, name, price FROM dbo.staged_products ORDER BY sku
                        """);
                Connection target = dataSource.getConnection();
                PreparedStatement merge = target.prepareStatement("""
                        INSERT INTO dbo.products (sku, name, price)
                        SELECT sku, name, price FROM ?
                        """)) {
            merge.unwrap(SQLServerPreparedStatement.class).setStructured(1, "dbo.ProductType", stagedProducts);
            copied = merge.executeUpdate();
        }

        assertThat(copied).isEqualTo(2);
        assertThat(priceOf("SKU-CHAIR")).isEqualByComparingTo(new BigDecimal("129.00"));
        assertThat(priceOf("SKU-HUB")).isEqualByComparingTo(new BigDecimal("39.90"));
    }

    @Test
    void duplicateKeysInTableParameterRollBackTheTransaction() throws Exception {
        SQLServerDataTable skus = new SQLServerDataTable();
        skus.addColumnMetadata("sku", Types.NVARCHAR);
        skus.addRow("SKU-DUPLICATE");
        skus.addRow("SKU-DUPLICATE");

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO dbo.products (sku, name, price)
                    VALUES ('SKU-PRECHECK', 'Inserted before failure', 1.00)
                    """)) {
                insert.executeUpdate();
            }
            try (PreparedStatement lookup = connection.prepareStatement("SELECT COUNT(*) FROM ?")) {
                lookup.unwrap(SQLServerPreparedStatement.class).setStructured(1, "dbo.SkuList", skus);
                assertThatThrownBy(lookup::executeQuery)
                        .isInstanceOfSatisfying(SQLServerException.class, failure -> {
                            assertThat(failure.getErrorCode()).isEqualTo(2627);
                            assertThat(failure.getMessage()).contains("PRIMARY KEY", "SKU-DUPLICATE");
                        });
            }
            connection.rollback();
        }

        try (Connection connection = dataSource.getConnection();
                PreparedStatement lookup = connection.prepareStatement("SELECT COUNT(*) FROM dbo.products WHERE sku = ?")) {
            lookup.setString(1, "SKU-PRECHECK");
            try (ResultSet row = lookup.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getInt(1)).isZero();
            }
        }
    }

    /// One line of a customer order as the application models it.
    record OrderLine(String sku, int quantity, BigDecimal unitPrice, String note) {
    }

    /// Streams order lines to the driver row by row instead of materializing a table.
    static final class OrderLineRecords implements ISQLServerDataRecord {

        private static final SQLServerMetaData[] COLUMNS = {
                new SQLServerMetaData("sku", Types.NVARCHAR, 32, 0),
                new SQLServerMetaData("quantity", Types.INTEGER),
                new SQLServerMetaData("unit_price", Types.DECIMAL, 10, 2),
                new SQLServerMetaData("note", Types.NVARCHAR, 200, 0)
        };

        private final Iterator<OrderLine> remaining;

        private OrderLine current;

        OrderLineRecords(List<OrderLine> lines) {
            this.remaining = lines.iterator();
        }

        @Override
        public SQLServerMetaData getColumnMetaData(int column) {
            return COLUMNS[column - 1];
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public Object[] getRowData() {
            return new Object[] {current.sku(), current.quantity(), current.unitPrice(), current.note()};
        }

        @Override
        public boolean next() {
            if (!remaining.hasNext()) {
                return false;
            }
            current = remaining.next();
            return true;
        }
    }

    private static BigDecimal priceOf(String sku) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement lookup = connection.prepareStatement("SELECT price FROM dbo.products WHERE sku = ?")) {
            lookup.setString(1, sku);
            try (ResultSet row = lookup.executeQuery()) {
                assertThat(row.next()).isTrue();
                return row.getBigDecimal(1);
            }
        }
    }

    private static void createSchema() throws SQLException {
        List<String> ddl = List.of(
                """
                CREATE TABLE dbo.products (
                    sku NVARCHAR(32) PRIMARY KEY,
                    name NVARCHAR(100) NOT NULL,
                    price DECIMAL(10, 2) NOT NULL)
                """,
                """
                INSERT INTO dbo.products (sku, name, price) VALUES
                    ('SKU-MOUSE', 'Wireless mouse', 24.50),
                    ('SKU-DESK', 'Standing desk', 349.00),
                    ('SKU-LAMP', 'Desk lamp', 18.75)
                """,
                """
                CREATE TABLE dbo.staged_products (
                    sku NVARCHAR(32) PRIMARY KEY,
                    name NVARCHAR(100) NOT NULL,
                    price DECIMAL(10, 2) NOT NULL)
                """,
                """
                INSERT INTO dbo.staged_products (sku, name, price) VALUES
                    ('SKU-CHAIR', 'Office chair', 129.00),
                    ('SKU-HUB', 'USB-C hub', 39.90)
                """,
                """
                CREATE TABLE dbo.order_lines (
                    id INT IDENTITY PRIMARY KEY,
                    order_id INT NOT NULL,
                    sku NVARCHAR(32) NOT NULL,
                    quantity INT NOT NULL,
                    unit_price DECIMAL(10, 2) NOT NULL,
                    note NVARCHAR(200) NULL)
                """,
                "CREATE TYPE dbo.SkuList AS TABLE (sku NVARCHAR(32) PRIMARY KEY)",
                """
                CREATE TYPE dbo.OrderLineType AS TABLE (
                    sku NVARCHAR(32) NOT NULL,
                    quantity INT NOT NULL,
                    unit_price DECIMAL(10, 2) NOT NULL,
                    note NVARCHAR(200) NULL)
                """,
                "CREATE TYPE dbo.PriceChangeType AS TABLE (sku NVARCHAR(32) NOT NULL, new_price DECIMAL(10, 2) NOT NULL)",
                """
                CREATE TYPE dbo.ProductType AS TABLE (
                    sku NVARCHAR(32) NOT NULL,
                    name NVARCHAR(100) NOT NULL,
                    price DECIMAL(10, 2) NOT NULL)
                """,
                """
                CREATE PROCEDURE dbo.apply_price_changes
                    @changes dbo.PriceChangeType READONLY, @changed INT OUTPUT
                AS
                BEGIN
                    SET NOCOUNT ON;
                    UPDATE p SET p.price = c.new_price
                    FROM dbo.products p JOIN @changes c ON c.sku = p.sku;
                    SET @changed = @@ROWCOUNT;
                END
                """);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : ddl) {
                statement.execute(sql);
            }
        }
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
