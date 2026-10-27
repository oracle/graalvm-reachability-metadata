/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_microsoft_sqlserver.mssql_jdbc;

import com.microsoft.sqlserver.jdbc.ISQLServerBulkData;
import com.microsoft.sqlserver.jdbc.SQLServerBulkCSVFileRecord;
import com.microsoft.sqlserver.jdbc.SQLServerBulkCopy;
import com.microsoft.sqlserver.jdbc.SQLServerBulkCopyOptions;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.microsoft.sqlserver.jdbc.SQLServerException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Loads rows into SQL Server tables with the driver's bulk copy API from CSV
/// files, application-supplied records, and result sets of another table.
///
/// The scenario is an order import job: a partner drops a CSV export, the job
/// bulk-loads it into a staging table, pushes corrections it computed in memory,
/// and archives the staged orders by streaming them from one table to another.
public class BulkCopyOrderImportTest {

    private static final String IMAGE = "mcr.microsoft.com/mssql/server:2022-CU14-ubuntu-22.04";

    private static final String PASSWORD = "BulkImport#2024";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(180);

    private static final String CONTAINER = "real-use-mssql-bulk-" + UUID.randomUUID();

    private static SQLServerDataSource dataSource;

    @TempDir
    static Path workDir;

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
        dataSource.setApplicationName("order-import-job");
        dataSource.setLoginTimeout(5);
        awaitServer();
        createSchema();
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @Test
    void csvExportIsBulkLoadedWithColumnMappings() throws Exception {
        Path export = workDir.resolve("partner-orders.csv");
        Files.writeString(export, """
                OrderRef,Customer,Amount,PlacedAt
                P-1001,Contoso,120.50,2024-03-01 09:15:00.000
                P-1002,Fabrikam,75.00,2024-03-01 10:30:00.000
                P-1003,Northwind,310.25,2024-03-02 14:45:00.000
                """, StandardCharsets.UTF_8);

        try (Connection connection = dataSource.getConnection();
                SQLServerBulkCSVFileRecord csv = new SQLServerBulkCSVFileRecord(
                        export.toString(), StandardCharsets.UTF_8.name(), ",", true);
                SQLServerBulkCopy bulkCopy = new SQLServerBulkCopy(connection)) {
            csv.addColumnMetadata(1, "OrderRef", Types.NVARCHAR, 16, 0);
            csv.addColumnMetadata(2, "Customer", Types.NVARCHAR, 64, 0);
            csv.addColumnMetadata(3, "Amount", Types.DECIMAL, 10, 2);
            csv.addColumnMetadata(4, "PlacedAt", Types.TIMESTAMP, 23, 3);

            SQLServerBulkCopyOptions options = new SQLServerBulkCopyOptions();
            options.setBatchSize(2);
            options.setCheckConstraints(true);
            bulkCopy.setBulkCopyOptions(options);
            bulkCopy.setDestinationTableName("dbo.staged_orders");
            bulkCopy.addColumnMapping("OrderRef", "order_ref");
            bulkCopy.addColumnMapping("Customer", "customer");
            bulkCopy.addColumnMapping("Amount", "amount");
            bulkCopy.addColumnMapping("PlacedAt", "placed_at");
            bulkCopy.writeToServer(csv);
        }

        Map<String, BigDecimal> amounts = amountsOf("dbo.staged_orders", "P-100%");
        assertThat(amounts).containsExactly(
                Map.entry("P-1001", new BigDecimal("120.50")),
                Map.entry("P-1002", new BigDecimal("75.00")),
                Map.entry("P-1003", new BigDecimal("310.25")));
        try (Connection connection = dataSource.getConnection(); Statement query = connection.createStatement();
                ResultSet row = query.executeQuery(
                        "SELECT customer, placed_at, source FROM dbo.staged_orders WHERE order_ref = 'P-1003'")) {
            assertThat(row.next()).isTrue();
            assertThat(row.getString("customer")).isEqualTo("Northwind");
            assertThat(row.getTimestamp("placed_at").toLocalDateTime())
                    .isEqualTo(LocalDateTime.of(2024, 3, 2, 14, 45));
            assertThat(row.getString("source")).isEqualTo("partner-feed");
        }
    }

    @Test
    void applicationRecordsAreStreamedThroughBulkDataSource() throws Exception {
        List<Correction> corrections = List.of(
                new Correction("C-2001", "Litware", new BigDecimal("15.99"), LocalDateTime.of(2024, 4, 1, 8, 0)),
                new Correction("C-2002", null, new BigDecimal("42.00"), LocalDateTime.of(2024, 4, 1, 8, 5)),
                new Correction("C-2003", "Adventure Works", new BigDecimal("9.10"), LocalDateTime.of(2024, 4, 2, 17, 30)));

        try (Connection connection = dataSource.getConnection();
                SQLServerBulkCopy bulkCopy = new SQLServerBulkCopy(connection)) {
            SQLServerBulkCopyOptions options = new SQLServerBulkCopyOptions();
            options.setKeepNulls(true);
            bulkCopy.setBulkCopyOptions(options);
            bulkCopy.setDestinationTableName("dbo.staged_orders");
            bulkCopy.addColumnMapping(1, "order_ref");
            bulkCopy.addColumnMapping(2, "customer");
            bulkCopy.addColumnMapping(3, "amount");
            bulkCopy.addColumnMapping(4, "placed_at");
            bulkCopy.writeToServer(new CorrectionBatch(corrections));
        }

        assertThat(amountsOf("dbo.staged_orders", "C-%")).containsExactly(
                Map.entry("C-2001", new BigDecimal("15.99")),
                Map.entry("C-2002", new BigDecimal("42.00")),
                Map.entry("C-2003", new BigDecimal("9.10")));
        try (Connection connection = dataSource.getConnection(); Statement query = connection.createStatement();
                ResultSet row = query.executeQuery(
                        "SELECT customer FROM dbo.staged_orders WHERE order_ref = 'C-2002'")) {
            assertThat(row.next()).isTrue();
            assertThat(row.getString("customer")).isNull();
            assertThat(row.wasNull()).isTrue();
        }
    }

    @Test
    void stagedOrdersAreArchivedFromAResultSet() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement insert = connection.createStatement()) {
            insert.executeUpdate("""
                    INSERT INTO dbo.staged_orders (order_ref, customer, amount, placed_at) VALUES
                        ('A-3001', 'Tailspin', 500.00, '2024-05-01T12:00:00'),
                        ('A-3002', 'Wingtip', 250.75, '2024-05-02T12:00:00')
                    """);
        }

        try (Connection source = dataSource.getConnection();
                Connection destination = dataSource.getConnection();
                Statement select = source.createStatement();
                ResultSet staged = select.executeQuery("""
                        SELECT order_ref, amount * 100 AS amount_cents, customer
                        FROM dbo.staged_orders WHERE order_ref LIKE 'A-%' ORDER BY order_ref
                        """);
                SQLServerBulkCopy bulkCopy = new SQLServerBulkCopy(destination)) {
            bulkCopy.setDestinationTableName("dbo.order_archive");
            bulkCopy.addColumnMapping("order_ref", "ref");
            bulkCopy.addColumnMapping("amount_cents", "cents");
            bulkCopy.addColumnMapping("customer", "customer_name");
            bulkCopy.writeToServer(staged);
        }

        Map<String, Long> archived = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection(); Statement query = connection.createStatement();
                ResultSet rows = query.executeQuery(
                        "SELECT ref, cents, customer_name FROM dbo.order_archive WHERE ref LIKE 'A-%' ORDER BY ref")) {
            while (rows.next()) {
                archived.put(rows.getString("ref") + "/" + rows.getString("customer_name"), rows.getLong("cents"));
            }
        }
        assertThat(archived).containsExactly(Map.entry("A-3001/Tailspin", 50_000L), Map.entry("A-3002/Wingtip", 25_075L));
    }

    @Test
    void constraintViolationRollsBackTheImportTransaction() throws Exception {
        List<Correction> corrections = List.of(
                new Correction("R-4001", "Proseware", new BigDecimal("10.00"), LocalDateTime.of(2024, 6, 1, 9, 0)),
                new Correction("R-4002", "Proseware", new BigDecimal("-3.00"), LocalDateTime.of(2024, 6, 1, 9, 1)));

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (SQLServerBulkCopy bulkCopy = new SQLServerBulkCopy(connection)) {
                SQLServerBulkCopyOptions options = new SQLServerBulkCopyOptions();
                options.setCheckConstraints(true);
                bulkCopy.setBulkCopyOptions(options);
                bulkCopy.setDestinationTableName("dbo.staged_orders");
                bulkCopy.addColumnMapping(1, "order_ref");
                bulkCopy.addColumnMapping(2, "customer");
                bulkCopy.addColumnMapping(3, "amount");
                bulkCopy.addColumnMapping(4, "placed_at");

                assertThatThrownBy(() -> bulkCopy.writeToServer(new CorrectionBatch(corrections)))
                        .isInstanceOf(SQLServerException.class)
                        .hasMessageContaining("positive_amount");
            }
            connection.rollback();
        }

        assertThat(amountsOf("dbo.staged_orders", "R-%")).isEmpty();
    }

    /// A correction the import job computed in memory before pushing it to staging.
    record Correction(String orderRef, String customer, BigDecimal amount, LocalDateTime placedAt) {
    }

    /// Exposes in-memory corrections to the bulk copy API as a row source.
    static final class CorrectionBatch implements ISQLServerBulkData {

        private static final long serialVersionUID = 1L;

        private final transient Iterator<Correction> remaining;

        private transient Correction current;

        CorrectionBatch(List<Correction> corrections) {
            this.remaining = corrections.iterator();
        }

        @Override
        public Set<Integer> getColumnOrdinals() {
            return Set.of(1, 2, 3, 4);
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 1 -> "order_ref";
                case 2 -> "customer";
                case 3 -> "amount";
                case 4 -> "placed_at";
                default -> throw new IllegalArgumentException("Unknown column " + column);
            };
        }

        @Override
        public int getColumnType(int column) {
            return switch (column) {
                case 1, 2 -> Types.NVARCHAR;
                case 3 -> Types.DECIMAL;
                case 4 -> Types.TIMESTAMP;
                default -> throw new IllegalArgumentException("Unknown column " + column);
            };
        }

        @Override
        public int getPrecision(int column) {
            return switch (column) {
                case 1 -> 16;
                case 2 -> 64;
                case 3 -> 10;
                case 4 -> 23;
                default -> throw new IllegalArgumentException("Unknown column " + column);
            };
        }

        @Override
        public int getScale(int column) {
            return switch (column) {
                case 3 -> 2;
                case 4 -> 3;
                default -> 0;
            };
        }

        @Override
        public Object[] getRowData() {
            return new Object[] {current.orderRef(), current.customer(), current.amount(),
                    Timestamp.valueOf(current.placedAt())};
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

    private static Map<String, BigDecimal> amountsOf(String table, String refPattern) throws SQLException {
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT order_ref, amount FROM " + table + " WHERE order_ref LIKE ? ORDER BY order_ref")) {
            query.setString(1, refPattern);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    amounts.put(rows.getString(1), rows.getBigDecimal(2));
                }
            }
        }
        return amounts;
    }

    private static void createSchema() throws SQLException {
        List<String> ddl = List.of(
                """
                CREATE TABLE dbo.staged_orders (
                    id INT IDENTITY PRIMARY KEY,
                    order_ref NVARCHAR(16) NOT NULL UNIQUE,
                    customer NVARCHAR(64) NULL CONSTRAINT default_customer DEFAULT 'walk-in',
                    amount DECIMAL(10, 2) NOT NULL CONSTRAINT positive_amount CHECK (amount > 0),
                    placed_at DATETIME NOT NULL,
                    source NVARCHAR(32) NULL CONSTRAINT default_source DEFAULT 'partner-feed')
                """,
                """
                CREATE TABLE dbo.order_archive (
                    ref NVARCHAR(16) PRIMARY KEY,
                    customer_name NVARCHAR(64) NULL,
                    cents BIGINT NOT NULL,
                    archived_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME())
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
