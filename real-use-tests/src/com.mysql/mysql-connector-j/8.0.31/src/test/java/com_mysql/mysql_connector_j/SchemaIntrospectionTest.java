/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mysql.mysql_connector_j;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/// Schema introspection through `DatabaseMetaData` and `ResultSetMetaData`: a migration tool
/// snapshots a shop database's tables, views, column types, primary keys, foreign-key rules and
/// unique indexes, then describes the columns of an ad-hoc report query.
public class SchemaIntrospectionTest {

    private static final String IMAGE = "container-registry.oracle.com/mysql/community-server:26.7.1";
    private static final String DATABASE = "shop";
    private static final String USER = "migrator";
    private static final String PASSWORD = "migrator-secret";
    private static final String CONTAINER = "real-use-mysql-schema-" + UUID.randomUUID();

    private static MysqlDataSource dataSource;

    @BeforeAll
    static void startMysql() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        docker("run", "--rm", "-d", "--name", CONTAINER,
                "-e", "MYSQL_RANDOM_ROOT_PASSWORD=yes",
                "-e", "MYSQL_DATABASE=" + DATABASE,
                "-e", "MYSQL_USER=" + USER,
                "-e", "MYSQL_PASSWORD=" + PASSWORD,
                "-p", "127.0.0.1:" + port + ":3306",
                IMAGE);
        dataSource = new MysqlDataSource();
        dataSource.setURL("jdbc:mysql://127.0.0.1:" + port + "/" + DATABASE);
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
    void snapshotsTablesViewsAndColumns() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            assertThat(meta.getDatabaseProductName()).isEqualTo("MySQL");
            assertThat(meta.getDriverName()).isEqualTo("MySQL Connector/J");
            assertThat(meta.getDriverMajorVersion()).isEqualTo(8);
            assertThat(meta.getUserName()).startsWith(USER + "@");
            assertThat(meta.supportsTransactions()).isTrue();
            assertThat(meta.supportsBatchUpdates()).isTrue();
            assertThat(meta.getIdentifierQuoteString()).isEqualTo("`");

            assertThat(tableTypes(meta)).containsExactly(
                    Map.entry("customer", "TABLE"),
                    Map.entry("customer_order", "TABLE"),
                    Map.entry("order_summary", "VIEW"));

            Map<String, Column> columns = columns(meta, "customer_order");
            assertThat(columns.keySet()).containsExactly("id", "customer_id", "total", "status", "placed_at", "note");
            assertThat(columns.get("id")).isEqualTo(new Column(Types.BIGINT, "BIGINT", 19, 0, false, true, null));
            assertThat(columns.get("customer_id")).isEqualTo(new Column(Types.INTEGER, "INT", 10, 0, false, false, null));
            assertThat(columns.get("total")).isEqualTo(new Column(Types.DECIMAL, "DECIMAL", 10, 2, false, false, "0.00"));
            assertThat(columns.get("status")).isEqualTo(new Column(Types.CHAR, "ENUM", 7, 0, false, false, "NEW"));
            assertThat(columns.get("placed_at").dataType()).isEqualTo(Types.TIMESTAMP);
            assertThat(columns.get("placed_at").typeName()).isEqualTo("DATETIME");
            assertThat(columns.get("note")).isEqualTo(new Column(Types.VARCHAR, "VARCHAR", 200, 0, true, false, null));
        }
    }

    @Test
    void snapshotsKeysAndIndexes() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();

            assertThat(primaryKey(meta, "customer")).containsExactly("id");
            assertThat(primaryKey(meta, "customer_order")).containsExactly("id");

            List<ForeignKey> imported = new ArrayList<>();
            try (ResultSet rows = meta.getImportedKeys(DATABASE, null, "customer_order")) {
                while (rows.next()) {
                    imported.add(new ForeignKey(rows.getString("FK_NAME"), rows.getString("FKCOLUMN_NAME"),
                            rows.getString("PKTABLE_NAME"), rows.getString("PKCOLUMN_NAME"),
                            rows.getShort("DELETE_RULE"), rows.getShort("UPDATE_RULE")));
                }
            }
            assertThat(imported).containsExactly(new ForeignKey("fk_order_customer", "customer_id",
                    "customer", "id", (short) DatabaseMetaData.importedKeyCascade,
                    (short) DatabaseMetaData.importedKeyRestrict));

            Set<String> referencing = new LinkedHashSet<>();
            try (ResultSet rows = meta.getExportedKeys(DATABASE, null, "customer")) {
                while (rows.next()) {
                    referencing.add(rows.getString("FKTABLE_NAME") + "." + rows.getString("FKCOLUMN_NAME"));
                }
            }
            assertThat(referencing).containsExactly("customer_order.customer_id");

            Map<String, List<String>> uniqueIndexes = new TreeMap<>();
            try (ResultSet rows = meta.getIndexInfo(DATABASE, null, "customer", true, false)) {
                while (rows.next()) {
                    assertThat(rows.getBoolean("NON_UNIQUE")).isFalse();
                    uniqueIndexes.computeIfAbsent(rows.getString("INDEX_NAME"), name -> new ArrayList<>())
                            .add(rows.getString("COLUMN_NAME"));
                }
            }
            assertThat(uniqueIndexes).containsOnly(
                    Map.entry("PRIMARY", List.of("id")),
                    Map.entry("uq_customer_email", List.of("tenant", "email")));
        }
    }

    @Test
    void describesReportQueryColumns() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT c.email AS customer_email, s.order_count, s.revenue, o.placed_at
                     FROM customer c
                     JOIN order_summary s ON s.customer_id = c.id
                     JOIN customer_order o ON o.customer_id = c.id
                     ORDER BY o.id
                     """)) {
            ResultSetMetaData described = rows.getMetaData();
            assertThat(described.getColumnCount()).isEqualTo(4);

            assertThat(described.getColumnLabel(1)).isEqualTo("customer_email");
            assertThat(described.getColumnName(1)).isEqualTo("email");
            assertThat(described.getTableName(1)).isEqualTo("customer");
            assertThat(described.getCatalogName(1)).isEqualTo(DATABASE);
            assertThat(described.getColumnType(1)).isEqualTo(Types.VARCHAR);
            assertThat(described.getColumnClassName(1)).isEqualTo(String.class.getName());
            assertThat(described.isNullable(1)).isEqualTo(ResultSetMetaData.columnNoNulls);

            assertThat(described.getColumnType(2)).isEqualTo(Types.BIGINT);
            assertThat(described.getColumnClassName(2)).isEqualTo(Long.class.getName());
            assertThat(described.getColumnType(3)).isEqualTo(Types.DECIMAL);
            assertThat(described.getScale(3)).isEqualTo(2);
            assertThat(described.getColumnType(4)).isEqualTo(Types.TIMESTAMP);

            Map<String, Object> firstRow = new LinkedHashMap<>();
            assertThat(rows.next()).isTrue();
            for (int column = 1; column <= described.getColumnCount(); column++) {
                Object value = rows.getObject(column);
                assertThat(value.getClass().getName()).isEqualTo(described.getColumnClassName(column));
                firstRow.put(described.getColumnLabel(column), value);
            }
            assertThat(firstRow.get("customer_email")).isEqualTo("ada@example.com");
            assertThat(firstRow.get("order_count")).isEqualTo(2L);
            assertThat(firstRow.get("revenue").toString()).isEqualTo("164.50");
        }
    }

    /// One column as the migration tool records it from `getColumns`.
    record Column(int dataType, String typeName, int size, int decimalDigits, boolean nullable,
                  boolean autoIncrement, String defaultValue) {
    }

    /// One imported foreign-key column with its referential actions.
    record ForeignKey(String name, String column, String referencedTable, String referencedColumn,
                      short deleteRule, short updateRule) {
    }

    private static Map<String, String> tableTypes(DatabaseMetaData meta) throws SQLException {
        Map<String, String> types = new TreeMap<>();
        try (ResultSet rows = meta.getTables(DATABASE, null, "%", new String[] {"TABLE", "VIEW"})) {
            while (rows.next()) {
                assertThat(rows.getString("TABLE_CAT")).isEqualTo(DATABASE);
                types.put(rows.getString("TABLE_NAME"), rows.getString("TABLE_TYPE"));
            }
        }
        return types;
    }

    private static Map<String, Column> columns(DatabaseMetaData meta, String table) throws SQLException {
        Map<String, Column> columns = new LinkedHashMap<>();
        try (ResultSet rows = meta.getColumns(DATABASE, null, table, "%")) {
            while (rows.next()) {
                columns.put(rows.getString("COLUMN_NAME"), new Column(
                        rows.getInt("DATA_TYPE"),
                        rows.getString("TYPE_NAME"),
                        rows.getInt("COLUMN_SIZE"),
                        rows.getInt("DECIMAL_DIGITS"),
                        rows.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                        "YES".equals(rows.getString("IS_AUTOINCREMENT")),
                        rows.getString("COLUMN_DEF")));
            }
        }
        return columns;
    }

    private static List<String> primaryKey(DatabaseMetaData meta, String table) throws SQLException {
        Map<Short, String> ordered = new TreeMap<>();
        try (ResultSet rows = meta.getPrimaryKeys(DATABASE, null, table)) {
            while (rows.next()) {
                ordered.put(rows.getShort("KEY_SEQ"), rows.getString("COLUMN_NAME"));
            }
        }
        return new ArrayList<>(ordered.values());
    }

    private static void createSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE customer (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        tenant VARCHAR(16) NOT NULL,
                        email VARCHAR(120) NOT NULL,
                        CONSTRAINT uq_customer_email UNIQUE (tenant, email)
                    ) ENGINE = InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE customer_order (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        customer_id INT NOT NULL,
                        total DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
                        status ENUM('NEW', 'PAID', 'SHIPPED') NOT NULL DEFAULT 'NEW',
                        placed_at DATETIME NOT NULL,
                        note VARCHAR(200),
                        CONSTRAINT fk_order_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
                            ON DELETE CASCADE ON UPDATE RESTRICT
                    ) ENGINE = InnoDB
                    """);
            statement.execute("""
                    CREATE VIEW order_summary AS
                    SELECT customer_id, COUNT(*) AS order_count, SUM(total) AS revenue
                    FROM customer_order
                    GROUP BY customer_id
                    """);
            statement.execute("INSERT INTO customer (tenant, email) VALUES ('eu', 'ada@example.com')");
            statement.execute("""
                    INSERT INTO customer_order (customer_id, total, status, placed_at) VALUES
                        (1, 120.00, 'PAID', '2026-03-01 09:30:00'),
                        (1, 44.50, 'NEW', '2026-03-02 16:05:00')
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
