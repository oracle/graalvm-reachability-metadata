/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_microsoft_sqlserver.mssql_jdbc;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.microsoft.sqlserver.jdbc.SQLServerDatabaseMetaData;
import com.microsoft.sqlserver.jdbc.SQLServerResultSetMetaData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/// Drives schema introspection: `DatabaseMetaData` catalog queries, `ResultSetMetaData`
/// of executed and unexecuted statements, and `ParameterMetaData` resolved both through
/// `sp_describe_undeclared_parameters` and through the `useFmtOnly` query parser.
///
/// The scenario is a reporting tool that discovers a sales warehouse on a real SQL
/// Server: it lists schemas, tables, columns, keys, indexes, and procedures, and then
/// describes report queries and their parameters before running them.
public class WarehouseSchemaIntrospectionTest {

    private static final String IMAGE = "mcr.microsoft.com/mssql/server:2022-CU14-ubuntu-22.04";

    private static final String PASSWORD = "Warehouse#Meta2024";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(180);

    private static final String CONTAINER = "real-use-mssql-warehouse-" + UUID.randomUUID();

    private static SQLServerDataSource warehouse;

    private static SQLServerDataSource fmtOnlyWarehouse;

    @BeforeAll
    static void startServer() throws Exception {
        docker("run", "-d", "--name", CONTAINER, "-e", "ACCEPT_EULA=Y", "-e", "MSSQL_SA_PASSWORD=" + PASSWORD,
                "-p", "127.0.0.1::1433", IMAGE);
        String mapping = docker("port", CONTAINER, "1433/tcp").lines().findFirst().orElseThrow();
        int port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));

        SQLServerDataSource admin = dataSource(port, "master");
        awaitServer(admin);
        try (Connection connection = admin.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE warehouse");
        }

        warehouse = dataSource(port, "warehouse");
        fmtOnlyWarehouse = dataSource(port, "warehouse");
        fmtOnlyWarehouse.setUseFmtOnly(true);
        try (Connection connection = warehouse.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA sales");
            statement.execute("""
                    CREATE TABLE sales.customers (
                        customer_id INT IDENTITY(1, 1) NOT NULL CONSTRAINT pk_customers PRIMARY KEY,
                        email VARCHAR(120) NOT NULL,
                        display_name NVARCHAR(80) NOT NULL,
                        region CHAR(2) NULL)
                    """);
            statement.execute("CREATE UNIQUE INDEX ux_customers_email ON sales.customers (email)");
            statement.execute("""
                    CREATE TABLE sales.orders (
                        order_id BIGINT IDENTITY(1000, 1) NOT NULL CONSTRAINT pk_orders PRIMARY KEY,
                        customer_id INT NOT NULL CONSTRAINT fk_orders_customers
                            REFERENCES sales.customers (customer_id) ON DELETE CASCADE,
                        amount DECIMAL(12, 2) NOT NULL,
                        placed_at DATETIME2(3) NOT NULL,
                        note NVARCHAR(MAX) NULL)
                    """);
            statement.execute("""
                    CREATE VIEW sales.customer_totals AS
                    SELECT c.customer_id, c.display_name, SUM(o.amount) AS total
                    FROM sales.customers c JOIN sales.orders o ON o.customer_id = c.customer_id
                    GROUP BY c.customer_id, c.display_name
                    """);
            statement.execute("""
                    CREATE PROCEDURE sales.orders_since @customer_id INT, @since DATETIME2(3), @count INT OUTPUT
                    AS
                    BEGIN
                        SELECT @count = COUNT(*) FROM sales.orders
                        WHERE customer_id = @customer_id AND placed_at >= @since;
                    END
                    """);
            statement.execute("""
                    INSERT INTO sales.customers (email, display_name, region) VALUES
                        ('ada@example.com', N'Ada', 'EU'),
                        ('lin@example.com', N'Lin', 'AP'),
                        ('sam@example.com', N'Sam', NULL)
                    """);
            statement.execute("""
                    INSERT INTO sales.orders (customer_id, amount, placed_at) VALUES
                        (1, 120.50, '2024-03-01T10:00:00'),
                        (1, 79.50, '2024-03-02T11:30:00'),
                        (2, 42.00, '2024-03-03T09:15:00')
                    """);
        }
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @Test
    void catalogListsSchemasTablesViewsAndColumns() throws Exception {
        try (Connection connection = warehouse.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            assertThat(metaData).isInstanceOf(SQLServerDatabaseMetaData.class);
            assertThat(metaData.getDatabaseProductName()).isEqualTo("Microsoft SQL Server");
            assertThat(metaData.getDatabaseMajorVersion()).isEqualTo(16);
            assertThat(metaData.getDriverName()).isEqualTo("Microsoft JDBC Driver 12.2 for SQL Server");
            assertThat(metaData.getDriverMajorVersion()).isEqualTo(12);
            assertThat(metaData.getIdentifierQuoteString()).isEqualTo("\"");
            assertThat(metaData.supportsTransactionIsolationLevel(Connection.TRANSACTION_SERIALIZABLE)).isTrue();

            List<String> catalogs = new ArrayList<>();
            try (ResultSet rows = metaData.getCatalogs()) {
                while (rows.next()) {
                    catalogs.add(rows.getString("TABLE_CAT"));
                }
            }
            assertThat(catalogs).contains("master", "warehouse");

            List<String> schemas = new ArrayList<>();
            try (ResultSet rows = metaData.getSchemas("warehouse", "s%")) {
                while (rows.next()) {
                    schemas.add(rows.getString("TABLE_SCHEM"));
                }
            }
            assertThat(schemas).contains("sales", "sys");

            Map<String, String> tables = new LinkedHashMap<>();
            try (ResultSet rows = metaData.getTables("warehouse", "sales", "%", new String[] {"TABLE", "VIEW"})) {
                while (rows.next()) {
                    tables.put(rows.getString("TABLE_NAME"), rows.getString("TABLE_TYPE"));
                }
            }
            assertThat(tables).containsExactly(
                    Map.entry("customers", "TABLE"),
                    Map.entry("orders", "TABLE"),
                    Map.entry("customer_totals", "VIEW"));

            List<String> columns = new ArrayList<>();
            try (ResultSet rows = metaData.getColumns("warehouse", "sales", "orders", "%")) {
                while (rows.next()) {
                    columns.add(rows.getInt("ORDINAL_POSITION") + ":" + rows.getString("COLUMN_NAME") + ":"
                            + rows.getString("TYPE_NAME") + ":" + rows.getInt("COLUMN_SIZE") + ":"
                            + rows.getInt("DECIMAL_DIGITS") + ":" + rows.getString("IS_NULLABLE") + ":"
                            + rows.getString("IS_AUTOINCREMENT"));
                }
            }
            assertThat(columns).containsExactly(
                    "1:order_id:bigint identity:19:0:NO:YES",
                    "2:customer_id:int:10:0:NO:NO",
                    "3:amount:decimal:12:2:NO:NO",
                    "4:placed_at:datetime2:23:3:NO:NO",
                    "5:note:nvarchar:2147483647:0:YES:NO");

            try (ResultSet rows = metaData.getColumns("warehouse", "sales", "customers", "region")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("DATA_TYPE")).isEqualTo(Types.CHAR);
                assertThat(rows.getInt("NULLABLE")).isEqualTo(DatabaseMetaData.columnNullable);
                assertThat(rows.next()).isFalse();
            }
        }
    }

    @Test
    void catalogDescribesKeysIndexesAndProcedures() throws Exception {
        try (Connection connection = warehouse.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            try (ResultSet rows = metaData.getPrimaryKeys("warehouse", "sales", "orders")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("COLUMN_NAME")).isEqualTo("order_id");
                assertThat(rows.getString("PK_NAME")).isEqualTo("pk_orders");
                assertThat(rows.getShort("KEY_SEQ")).isEqualTo((short) 1);
                assertThat(rows.next()).isFalse();
            }

            try (ResultSet rows = metaData.getImportedKeys("warehouse", "sales", "orders")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("PKTABLE_NAME")).isEqualTo("customers");
                assertThat(rows.getString("PKCOLUMN_NAME")).isEqualTo("customer_id");
                assertThat(rows.getString("FKCOLUMN_NAME")).isEqualTo("customer_id");
                assertThat(rows.getString("FK_NAME")).isEqualTo("fk_orders_customers");
                assertThat(rows.getShort("DELETE_RULE")).isEqualTo((short) DatabaseMetaData.importedKeyCascade);
                assertThat(rows.getShort("UPDATE_RULE")).isEqualTo((short) DatabaseMetaData.importedKeyNoAction);
                assertThat(rows.next()).isFalse();
            }

            try (ResultSet rows = metaData.getExportedKeys("warehouse", "sales", "customers")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("FKTABLE_NAME")).isEqualTo("orders");
                assertThat(rows.next()).isFalse();
            }

            Map<String, Boolean> uniqueByIndex = new LinkedHashMap<>();
            try (ResultSet rows = metaData.getIndexInfo("warehouse", "sales", "customers", false, false)) {
                while (rows.next()) {
                    String name = rows.getString("INDEX_NAME");
                    if (name != null) {
                        uniqueByIndex.put(name + "(" + rows.getString("COLUMN_NAME") + ")",
                                !rows.getBoolean("NON_UNIQUE"));
                    }
                }
            }
            assertThat(uniqueByIndex).containsOnly(
                    Map.entry("pk_customers(customer_id)", true),
                    Map.entry("ux_customers_email(email)", true));

            List<String> procedures = new ArrayList<>();
            try (ResultSet rows = metaData.getProcedures("warehouse", "sales", "orders%")) {
                while (rows.next()) {
                    procedures.add(rows.getString("PROCEDURE_NAME"));
                }
            }
            assertThat(procedures).containsExactly("orders_since;1");

            List<String> parameters = new ArrayList<>();
            try (ResultSet rows = metaData.getProcedureColumns("warehouse", "sales", "orders_since", "%")) {
                while (rows.next()) {
                    parameters.add(rows.getString("COLUMN_NAME") + ":" + rows.getShort("COLUMN_TYPE") + ":"
                            + rows.getString("TYPE_NAME"));
                }
            }
            assertThat(parameters).containsExactly(
                    "@customer_id:" + DatabaseMetaData.procedureColumnIn + ":int",
                    "@since:" + DatabaseMetaData.procedureColumnIn + ":datetime2",
                    "@count:" + DatabaseMetaData.procedureColumnInOut + ":int");
        }
    }

    @Test
    void reportQueryIsDescribedBeforeAndAfterExecution() throws Exception {
        String report = """
                SELECT c.display_name AS customer, c.region, o.order_id, o.amount, o.placed_at
                FROM sales.orders o JOIN sales.customers c ON c.customer_id = o.customer_id
                WHERE o.amount >= ?
                ORDER BY o.order_id
                """;
        try (Connection connection = warehouse.getConnection();
                PreparedStatement query = connection.prepareStatement(report)) {
            ResultSetMetaData described = query.getMetaData();
            assertThat(described).isInstanceOf(SQLServerResultSetMetaData.class);
            assertThat(described.getColumnCount()).isEqualTo(5);
            assertThat(described.getColumnLabel(1)).isEqualTo("customer");
            assertThat(described.getColumnType(1)).isEqualTo(Types.NVARCHAR);
            assertThat(described.getColumnDisplaySize(1)).isEqualTo(80);
            assertThat(described.getColumnTypeName(2)).isEqualTo("char");
            assertThat(described.isNullable(2)).isEqualTo(ResultSetMetaData.columnNullable);
            assertThat(described.getColumnTypeName(3)).isEqualTo("bigint");
            assertThat(described.getColumnType(4)).isEqualTo(Types.DECIMAL);
            assertThat(described.getPrecision(4)).isEqualTo(12);
            assertThat(described.getScale(4)).isEqualTo(2);
            assertThat(described.getColumnClassName(4)).isEqualTo("java.math.BigDecimal");
            assertThat(described.getColumnTypeName(5)).isEqualTo("datetime2");
            assertThat(described.getColumnClassName(5)).isEqualTo("java.sql.Timestamp");

            query.setBigDecimal(1, new BigDecimal("50.00"));
            List<String> lines = new ArrayList<>();
            try (ResultSet rows = query.executeQuery()) {
                ResultSetMetaData executed = rows.getMetaData();
                assertThat(executed.getColumnCount()).isEqualTo(5);
                assertThat(executed.getColumnName(4)).isEqualTo("amount");
                assertThat(executed.getColumnTypeName(3)).isEqualTo("bigint");
                assertThat(executed.isAutoIncrement(3)).isTrue();
                assertThat(executed.isAutoIncrement(4)).isFalse();
                assertThat(executed.isNullable(4)).isEqualTo(ResultSetMetaData.columnNoNulls);
                while (rows.next()) {
                    lines.add(rows.getString("customer") + ":" + rows.getLong("order_id") + ":"
                            + rows.getBigDecimal("amount"));
                }
            }
            assertThat(lines).containsExactly("Ada:1000:120.50", "Ada:1001:79.50");
        }
    }

    @Test
    void parametersAreDescribedByTheServer() throws Exception {
        try (Connection connection = warehouse.getConnection();
                PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO sales.orders (customer_id, amount, placed_at, note) VALUES (?, ?, ?, ?)
                        """)) {
            assertParameters(insert.getParameterMetaData());
        }
    }

    @Test
    void parametersAreDescribedThroughTheFmtOnlyParser() throws Exception {
        try (Connection connection = fmtOnlyWarehouse.getConnection();
                PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO sales.orders (customer_id, amount, placed_at, note) VALUES (?, ?, ?, ?)
                        """)) {
            assertParameters(insert.getParameterMetaData());
        }

        try (Connection connection = fmtOnlyWarehouse.getConnection();
                PreparedStatement lookup = connection.prepareStatement("""
                        SELECT o.order_id FROM sales.orders AS o
                        WHERE o.customer_id = ? AND o.placed_at >= ?
                        """)) {
            ParameterMetaData parameters = lookup.getParameterMetaData();
            assertThat(parameters.getParameterCount()).isEqualTo(2);
            assertThat(parameters.getParameterType(1)).isEqualTo(Types.INTEGER);
            assertThat(parameters.getParameterTypeName(2)).isEqualTo("datetime2");

            lookup.setInt(1, 1);
            lookup.setTimestamp(2, Timestamp.valueOf("2024-03-02 00:00:00"));
            try (ResultSet rows = lookup.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(1001L);
                assertThat(rows.next()).isFalse();
            }
        }
    }

    private static void assertParameters(ParameterMetaData parameters) throws SQLException {
        assertThat(parameters.getParameterCount()).isEqualTo(4);
        assertThat(parameters.getParameterType(1)).isEqualTo(Types.INTEGER);
        assertThat(parameters.getParameterTypeName(1)).isEqualTo("int");
        assertThat(parameters.getParameterType(2)).isEqualTo(Types.DECIMAL);
        assertThat(parameters.getPrecision(2)).isEqualTo(12);
        assertThat(parameters.getScale(2)).isEqualTo(2);
        assertThat(parameters.getParameterTypeName(3)).isEqualTo("datetime2");
        assertThat(parameters.getParameterType(4)).isEqualTo(Types.NVARCHAR);
        assertThat(parameters.getParameterMode(4)).isEqualTo(ParameterMetaData.parameterModeIn);
        assertThat(parameters.getParameterClassName(2)).isEqualTo("java.math.BigDecimal");
    }

    private static SQLServerDataSource dataSource(int port, String database) {
        SQLServerDataSource dataSource = new SQLServerDataSource();
        dataSource.setServerName("127.0.0.1");
        dataSource.setPortNumber(port);
        dataSource.setDatabaseName(database);
        dataSource.setUser("sa");
        dataSource.setPassword(PASSWORD);
        dataSource.setEncrypt("false");
        dataSource.setLoginTimeout(5);
        return dataSource;
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
