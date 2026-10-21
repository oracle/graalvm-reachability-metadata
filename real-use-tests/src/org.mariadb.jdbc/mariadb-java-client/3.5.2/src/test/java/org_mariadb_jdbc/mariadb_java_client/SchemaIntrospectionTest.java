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
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/// Schema introspection through `DatabaseMetaData`, `ResultSetMetaData`, and
/// `ParameterMetaData`: tables and views, column definitions, primary, foreign and
/// index keys, and the shape of query results and statement parameters.
///
/// The scenario is a schema-migration tool that connects through the driver's own
/// `MariaDbDataSource`, reads back the live schema of a small library catalog, and
/// compares it with the model it expects before deciding what to migrate.
public class SchemaIntrospectionTest {

    private static final String IMAGE = "mariadb:13.0";

    private static final String CONTAINER = "real-use-mariadb-schema-" + UUID.randomUUID();

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);

    private static final String DATABASE = "catalog";

    private static final String USER = "catalog_migrator";

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

        dataSource = new MariaDbDataSource();
        dataSource.setUrl("jdbc:mariadb://127.0.0.1:" + port + "/" + DATABASE + "?useServerPrepStmts=true");
        dataSource.setUser(USER);
        dataSource.setPassword(PASSWORD);
        awaitServer();

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE authors (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        full_name VARCHAR(120) NOT NULL,
                        country CHAR(2) DEFAULT 'US'
                    ) ENGINE=InnoDB COMMENT='People who write books'""");
            statement.execute("""
                    CREATE TABLE books (
                        isbn CHAR(13) NOT NULL,
                        edition SMALLINT NOT NULL,
                        author_id INT NOT NULL,
                        title VARCHAR(200) NOT NULL,
                        price DECIMAL(8, 2),
                        published DATE,
                        PRIMARY KEY (isbn, edition),
                        UNIQUE KEY uk_books_title (title, edition),
                        KEY idx_books_published (published),
                        CONSTRAINT fk_books_author FOREIGN KEY (author_id) REFERENCES authors (id)
                            ON DELETE CASCADE ON UPDATE RESTRICT
                    ) ENGINE=InnoDB""");
            statement.execute("""
                    CREATE VIEW book_listing AS
                        SELECT b.title, a.full_name AS author FROM books b JOIN authors a ON a.id = b.author_id""");
        }
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @Test
    void reportsServerAndDriverIdentity() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();

            assertThat(meta.getDatabaseProductName()).isEqualTo("MariaDB");
            assertThat(meta.getDatabaseMajorVersion()).isEqualTo(13);
            assertThat(meta.getDriverName()).isEqualTo("MariaDB Connector/J");
            assertThat(meta.getDriverVersion()).isEqualTo("3.5.2");
            assertThat(meta.getUserName()).isEqualTo(USER);
            assertThat(meta.supportsTransactions()).isTrue();
            assertThat(meta.supportsTransactionIsolationLevel(Connection.TRANSACTION_SERIALIZABLE)).isTrue();
            assertThat(meta.getIdentifierQuoteString()).isEqualTo("`");
            assertThat(connection.getCatalog()).isEqualTo(DATABASE);
        }
    }

    @Test
    void listsTablesAndViewsWithRemarks() throws Exception {
        SchemaReader reader = new SchemaReader(dataSource);

        Map<String, String> tables = reader.tables("TABLE");
        Map<String, String> views = reader.tables("VIEW");

        assertThat(tables).containsOnlyKeys("authors", "books");
        assertThat(tables.get("authors")).isEqualTo("People who write books");
        assertThat(views).containsOnlyKeys("book_listing");
    }

    @Test
    void describesColumnDefinitions() throws Exception {
        SchemaReader reader = new SchemaReader(dataSource);

        List<ColumnModel> authors = reader.columns("authors");
        assertThat(authors).extracting(ColumnModel::name).containsExactly("id", "full_name", "country");
        assertThat(authors.get(0)).isEqualTo(new ColumnModel("id", Types.INTEGER, "INT", 10, false, true, null));
        assertThat(authors.get(1)).isEqualTo(
                new ColumnModel("full_name", Types.VARCHAR, "VARCHAR", 120, false, false, null));
        assertThat(authors.get(2).typeName()).isEqualTo("CHAR");
        assertThat(authors.get(2).nullable()).isTrue();
        assertThat(authors.get(2).defaultValue()).contains("US");

        List<ColumnModel> books = reader.columns("books");
        ColumnModel price = books.stream().filter(column -> column.name().equals("price")).findFirst().orElseThrow();
        assertThat(price.sqlType()).isEqualTo(Types.DECIMAL);
        assertThat(price.size()).isEqualTo(8);
        assertThat(price.nullable()).isTrue();
        ColumnModel published = books.stream()
                .filter(column -> column.name().equals("published")).findFirst().orElseThrow();
        assertThat(published.sqlType()).isEqualTo(Types.DATE);
    }

    @Test
    void readsPrimaryForeignAndIndexKeys() throws Exception {
        SchemaReader reader = new SchemaReader(dataSource);

        assertThat(reader.primaryKey("books")).containsExactly("isbn", "edition");
        assertThat(reader.primaryKey("authors")).containsExactly("id");

        List<ForeignKeyModel> imported = reader.importedKeys("books");
        assertThat(imported).containsExactly(new ForeignKeyModel("fk_books_author", "author_id", "authors", "id",
                DatabaseMetaData.importedKeyRestrict, DatabaseMetaData.importedKeyCascade));
        assertThat(reader.exportedKeys("authors")).containsExactly("books.author_id");

        Map<String, List<String>> uniqueIndexes = reader.indexes("books", true);
        assertThat(uniqueIndexes).containsOnlyKeys("PRIMARY", "uk_books_title");
        assertThat(uniqueIndexes.get("uk_books_title")).containsExactly("title", "edition");
        Map<String, List<String>> allIndexes = reader.indexes("books", false);
        assertThat(allIndexes).containsKeys("idx_books_published", "fk_books_author");
        assertThat(allIndexes.get("idx_books_published")).containsExactly("published");
    }

    @Test
    void describesQueryResultsAndParameters() throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement("""
                        SELECT b.isbn, b.title AS book_title, a.full_name, b.price, COUNT(*) OVER () AS total
                        FROM books b JOIN authors a ON a.id = b.author_id
                        WHERE b.price < ? AND a.country = ?""")) {
            ParameterMetaData parameters = query.getParameterMetaData();
            assertThat(parameters.getParameterCount()).isEqualTo(2);

            ResultSetMetaData columns = query.getMetaData();
            assertThat(columns.getColumnCount()).isEqualTo(5);
            assertThat(columns.getColumnLabel(2)).isEqualTo("book_title");
            assertThat(columns.getColumnName(2)).isEqualTo("title");
            assertThat(columns.getTableName(3)).isEqualTo("authors");
            assertThat(columns.getCatalogName(1)).isEqualTo(DATABASE);
            assertThat(columns.getColumnType(1)).isEqualTo(Types.CHAR);
            assertThat(columns.getColumnType(4)).isEqualTo(Types.DECIMAL);
            assertThat(columns.getPrecision(4)).isEqualTo(8);
            assertThat(columns.getScale(4)).isEqualTo(2);
            assertThat(columns.isNullable(5)).isEqualTo(ResultSetMetaData.columnNoNulls);
            assertThat(columns.getColumnClassName(4)).isEqualTo("java.math.BigDecimal");
            assertThat(columns.getColumnType(5)).isEqualTo(Types.BIGINT);
        }
    }

    @Test
    void executedQueryReportsColumnConstraints() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT id, full_name, country FROM authors")) {
            ResultSetMetaData columns = rows.getMetaData();

            assertThat(columns.isAutoIncrement(1)).isTrue();
            assertThat(columns.isAutoIncrement(2)).isFalse();
            assertThat(columns.isNullable(1)).isEqualTo(ResultSetMetaData.columnNoNulls);
            assertThat(columns.isNullable(2)).isEqualTo(ResultSetMetaData.columnNoNulls);
            assertThat(columns.isNullable(3)).isEqualTo(ResultSetMetaData.columnNullable);
            assertThat(columns.getColumnDisplaySize(2)).isEqualTo(120);
            assertThat(columns.getColumnTypeName(3)).isEqualTo("CHAR");
        }
    }

    record ColumnModel(String name, int sqlType, String typeName, int size, boolean nullable,
            boolean autoIncrement, String defaultValue) {
    }

    record ForeignKeyModel(String name, String column, String referencedTable, String referencedColumn,
            int updateRule, int deleteRule) {
    }

    /// The migration tool's view of the live schema, read through `DatabaseMetaData`.
    static final class SchemaReader {

        private final MariaDbDataSource dataSource;

        SchemaReader(MariaDbDataSource dataSource) {
            this.dataSource = dataSource;
        }

        Map<String, String> tables(String type) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                    ResultSet rows = connection.getMetaData()
                            .getTables(connection.getCatalog(), null, "%", new String[] {type})) {
                Map<String, String> tables = new LinkedHashMap<>();
                while (rows.next()) {
                    assertThat(rows.getString("TABLE_TYPE")).isEqualTo(type);
                    tables.put(rows.getString("TABLE_NAME"), rows.getString("REMARKS"));
                }
                return tables;
            }
        }

        List<ColumnModel> columns(String table) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                    ResultSet rows = connection.getMetaData().getColumns(connection.getCatalog(), null, table, "%")) {
                List<ColumnModel> columns = new ArrayList<>();
                while (rows.next()) {
                    columns.add(new ColumnModel(
                            rows.getString("COLUMN_NAME"),
                            rows.getInt("DATA_TYPE"),
                            rows.getString("TYPE_NAME"),
                            rows.getInt("COLUMN_SIZE"),
                            rows.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                            "YES".equals(rows.getString("IS_AUTOINCREMENT")),
                            rows.getString("COLUMN_DEF")));
                }
                return columns;
            }
        }

        List<String> primaryKey(String table) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                    ResultSet rows = connection.getMetaData().getPrimaryKeys(connection.getCatalog(), null, table)) {
                Map<Integer, String> bySequence = new TreeMap<>();
                while (rows.next()) {
                    bySequence.put(rows.getInt("KEY_SEQ"), rows.getString("COLUMN_NAME"));
                }
                return new ArrayList<>(bySequence.values());
            }
        }

        List<ForeignKeyModel> importedKeys(String table) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                    ResultSet rows = connection.getMetaData().getImportedKeys(connection.getCatalog(), null, table)) {
                List<ForeignKeyModel> keys = new ArrayList<>();
                while (rows.next()) {
                    keys.add(new ForeignKeyModel(
                            rows.getString("FK_NAME"),
                            rows.getString("FKCOLUMN_NAME"),
                            rows.getString("PKTABLE_NAME"),
                            rows.getString("PKCOLUMN_NAME"),
                            rows.getInt("UPDATE_RULE"),
                            rows.getInt("DELETE_RULE")));
                }
                return keys;
            }
        }

        List<String> exportedKeys(String table) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                    ResultSet rows = connection.getMetaData().getExportedKeys(connection.getCatalog(), null, table)) {
                List<String> references = new ArrayList<>();
                while (rows.next()) {
                    references.add(rows.getString("FKTABLE_NAME") + "." + rows.getString("FKCOLUMN_NAME"));
                }
                return references;
            }
        }

        Map<String, List<String>> indexes(String table, boolean uniqueOnly) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                    ResultSet rows = connection.getMetaData()
                            .getIndexInfo(connection.getCatalog(), null, table, uniqueOnly, false)) {
                Map<String, List<String>> indexes = new LinkedHashMap<>();
                while (rows.next()) {
                    assertThat(rows.getBoolean("NON_UNIQUE")).isEqualTo(!rows.getString("INDEX_NAME")
                            .equals("PRIMARY") && !rows.getString("INDEX_NAME").startsWith("uk_"));
                    indexes.computeIfAbsent(rows.getString("INDEX_NAME"), name -> new ArrayList<>())
                            .add(rows.getString("COLUMN_NAME"));
                }
                return indexes;
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
