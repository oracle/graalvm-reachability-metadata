/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_postgresql.postgresql;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.postgresql.PGResultSetMetaData;
import org.postgresql.ds.PGSimpleDataSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Inspects a live schema through JDBC metadata introspection: `DatabaseMetaData` catalog
 * queries, `ResultSetMetaData` and `ParameterMetaData`.
 *
 * A schema-documentation tool connects to a library database, lists its tables and views,
 * describes each column with its JDBC type, size and nullability, follows primary and
 * foreign keys and unique indexes, and checks that a report query and its bind parameters
 * are typed the way the data-access layer expects before it runs the query.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchemaCatalogIntrospectionTest {

    private static final String POSTGRES_IMAGE = "postgres:18-alpine";

    private static final String DATABASE = "library";

    private static final String USER = "librarian";

    private static final String PASSWORD = "s3cret";

    private static final String SCHEMA = "lending";

    private final String containerName = "real-use-postgresql-" + UUID.randomUUID();

    private PGSimpleDataSource dataSource;

    /// One column as the documentation tool reports it.
    record ColumnDescription(String name, int jdbcType, String typeName, int size, boolean nullable,
            boolean autoIncrement) {
    }

    /// One foreign-key edge from a referencing column to a referenced primary key column.
    record ForeignKey(String name, String fromTable, String fromColumn, String toTable, String toColumn,
            int deleteRule) {
    }

    @BeforeAll
    void startDatabase() throws Exception {
        int port = freePort();
        Process run = new ProcessBuilder("docker", "run", "-d", "--rm", "--name", containerName,
                "-p", port + ":5432",
                "-e", "POSTGRES_DB=" + DATABASE,
                "-e", "POSTGRES_USER=" + USER,
                "-e", "POSTGRES_PASSWORD=" + PASSWORD,
                POSTGRES_IMAGE)
                .inheritIO()
                .start();
        assertThat(run.waitFor(120, TimeUnit.SECONDS)).isTrue();
        assertThat(run.exitValue()).isZero();

        dataSource = new PGSimpleDataSource();
        dataSource.setServerNames(new String[] {"127.0.0.1"});
        dataSource.setPortNumbers(new int[] {port});
        dataSource.setDatabaseName(DATABASE);
        dataSource.setUser(USER);
        dataSource.setPassword(PASSWORD);
        dataSource.setConnectTimeout(5);
        awaitDatabase();

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
            statement.execute("""
                    CREATE TABLE lending.members (
                        id         bigserial    PRIMARY KEY,
                        email      varchar(120) NOT NULL UNIQUE,
                        full_name  text         NOT NULL,
                        joined_on  date         NOT NULL DEFAULT current_date
                    )""");
            statement.execute("COMMENT ON TABLE lending.members IS 'Registered library members'");
            statement.execute("""
                    CREATE TABLE lending.books (
                        isbn       char(13)      PRIMARY KEY,
                        title      varchar(200)  NOT NULL,
                        price      numeric(8, 2),
                        available  boolean       NOT NULL DEFAULT true
                    )""");
            statement.execute("""
                    CREATE TABLE lending.loans (
                        id         serial    PRIMARY KEY,
                        member_id  bigint    NOT NULL,
                        isbn       char(13)  NOT NULL,
                        due_at     timestamp with time zone NOT NULL,
                        CONSTRAINT loans_member_fk FOREIGN KEY (member_id)
                            REFERENCES lending.members (id) ON DELETE CASCADE,
                        CONSTRAINT loans_book_fk FOREIGN KEY (isbn)
                            REFERENCES lending.books (isbn) ON DELETE RESTRICT
                    )""");
            statement.execute("CREATE INDEX loans_due_idx ON lending.loans (due_at)");
            statement.execute("""
                    CREATE VIEW lending.overdue_loans AS
                        SELECT l.id, m.email, b.title, l.due_at
                        FROM lending.loans l
                        JOIN lending.members m ON m.id = l.member_id
                        JOIN lending.books b ON b.isbn = l.isbn
                        WHERE l.due_at < now()""");
        }
    }

    @AfterAll
    void stopDatabase() throws Exception {
        Process remove = new ProcessBuilder("docker", "rm", "-f", containerName).inheritIO().start();
        remove.waitFor(60, TimeUnit.SECONDS);
    }

    @Test
    void documentsTablesColumnsAndKeysOfTheLendingSchema() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData catalog = connection.getMetaData();
            assertThat(catalog.getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(catalog.getDatabaseMajorVersion()).isEqualTo(18);
            assertThat(catalog.getDriverName()).isEqualTo("PostgreSQL JDBC Driver");
            assertThat(catalog.getDriverVersion()).isEqualTo("42.7.3");
            assertThat(catalog.getUserName()).isEqualTo(USER);
            assertThat(catalog.getIdentifierQuoteString()).isEqualTo("\"");
            assertThat(catalog.supportsTransactions()).isTrue();

            assertThat(schemas(catalog)).contains(SCHEMA, "public");

            Map<String, String> tables = new LinkedHashMap<>();
            Map<String, String> remarks = new LinkedHashMap<>();
            try (ResultSet found = catalog.getTables(null, SCHEMA, "%", new String[] {"TABLE", "VIEW"})) {
                while (found.next()) {
                    assertThat(found.getString("TABLE_SCHEM")).isEqualTo(SCHEMA);
                    tables.put(found.getString("TABLE_NAME"), found.getString("TABLE_TYPE"));
                    remarks.put(found.getString("TABLE_NAME"), found.getString("REMARKS"));
                }
            }
            assertThat(tables).containsOnly(
                    Map.entry("books", "TABLE"),
                    Map.entry("loans", "TABLE"),
                    Map.entry("members", "TABLE"),
                    Map.entry("overdue_loans", "VIEW"));
            assertThat(remarks.get("members")).isEqualTo("Registered library members");
            assertThat(remarks.get("books")).isNull();

            List<ColumnDescription> members = columns(catalog, "members");
            assertThat(members).containsExactly(
                    new ColumnDescription("id", Types.BIGINT, "bigserial", 19, false, true),
                    new ColumnDescription("email", Types.VARCHAR, "varchar", 120, false, false),
                    new ColumnDescription("full_name", Types.VARCHAR, "text", Integer.MAX_VALUE, false, false),
                    new ColumnDescription("joined_on", Types.DATE, "date", 13, false, false));

            List<ColumnDescription> books = columns(catalog, "books");
            assertThat(books).containsExactly(
                    new ColumnDescription("isbn", Types.CHAR, "bpchar", 13, false, false),
                    new ColumnDescription("title", Types.VARCHAR, "varchar", 200, false, false),
                    new ColumnDescription("price", Types.NUMERIC, "numeric", 8, true, false),
                    new ColumnDescription("available", Types.BIT, "bool", 1, false, false));
            try (ResultSet price = catalog.getColumns(null, SCHEMA, "books", "price")) {
                assertThat(price.next()).isTrue();
                assertThat(price.getInt("DECIMAL_DIGITS")).isEqualTo(2);
                assertThat(price.getString("IS_NULLABLE")).isEqualTo("YES");
            }
            try (ResultSet available = catalog.getColumns(null, SCHEMA, "books", "available")) {
                assertThat(available.next()).isTrue();
                assertThat(available.getString("COLUMN_DEF")).isEqualTo("true");
            }

            assertThat(primaryKey(catalog, "loans")).containsExactly("id");
            assertThat(primaryKey(catalog, "books")).containsExactly("isbn");

            List<ForeignKey> loanReferences = new ArrayList<>();
            try (ResultSet imported = catalog.getImportedKeys(null, SCHEMA, "loans")) {
                while (imported.next()) {
                    loanReferences.add(foreignKey(imported));
                }
            }
            assertThat(loanReferences).containsExactlyInAnyOrder(
                    new ForeignKey("loans_member_fk", "loans", "member_id", "members", "id",
                            DatabaseMetaData.importedKeyCascade),
                    new ForeignKey("loans_book_fk", "loans", "isbn", "books", "isbn",
                            DatabaseMetaData.importedKeyRestrict));

            List<ForeignKey> memberDependents = new ArrayList<>();
            try (ResultSet exported = catalog.getExportedKeys(null, SCHEMA, "members")) {
                while (exported.next()) {
                    memberDependents.add(foreignKey(exported));
                }
            }
            assertThat(memberDependents).extracting(ForeignKey::fromTable, ForeignKey::fromColumn)
                    .containsExactly(tuple("loans", "member_id"));

            Map<String, Boolean> memberIndexes = indexes(catalog, "members", false);
            assertThat(memberIndexes).containsOnly(
                    Map.entry("members_pkey", false),
                    Map.entry("members_email_key", false));
            Map<String, Boolean> loanIndexes = indexes(catalog, "loans", false);
            assertThat(loanIndexes).containsOnly(
                    Map.entry("loans_pkey", false),
                    Map.entry("loans_due_idx", true));
            assertThat(indexes(catalog, "loans", true)).containsOnlyKeys("loans_pkey");
        }
    }

    @Test
    void describesReportQueryResultColumnsAndBindParameters() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("TRUNCATE lending.loans, lending.members, lending.books");
                statement.execute("""
                        INSERT INTO lending.members (email, full_name) VALUES ('ada@example.com', 'Ada Lovelace')""");
                statement.execute("""
                        INSERT INTO lending.books (isbn, title, price)
                        VALUES ('9780262033848', 'Introduction to Algorithms', 94.50)""");
                statement.execute("""
                        INSERT INTO lending.loans (member_id, isbn, due_at)
                        SELECT id, '9780262033848', now() - interval '3 days' FROM lending.members""");
            }

            String report = """
                    SELECT m.email, b.title AS book_title, b.price, l.due_at, b.available,
                           count(*) OVER () AS total_overdue
                    FROM lending.loans l
                    JOIN lending.members m ON m.id = l.member_id
                    JOIN lending.books b ON b.isbn = l.isbn
                    WHERE l.due_at < ? AND b.price >= ? AND m.email LIKE ?""";
            try (PreparedStatement query = connection.prepareStatement(report)) {
                ParameterMetaData parameters = query.getParameterMetaData();
                assertThat(parameters.getParameterCount()).isEqualTo(3);
                assertThat(parameters.getParameterType(1)).isEqualTo(Types.TIMESTAMP);
                assertThat(parameters.getParameterTypeName(1)).isEqualTo("timestamptz");
                assertThat(parameters.getParameterType(2)).isEqualTo(Types.NUMERIC);
                assertThat(parameters.getParameterClassName(2)).isEqualTo(BigDecimal.class.getName());
                assertThat(parameters.getParameterType(3)).isEqualTo(Types.VARCHAR);
                assertThat(parameters.getParameterMode(3)).isEqualTo(ParameterMetaData.parameterModeIn);

                ResultSetMetaData beforeRun = query.getMetaData();
                assertThat(beforeRun.getColumnCount()).isEqualTo(6);
                assertThat(beforeRun.getColumnLabel(2)).isEqualTo("book_title");

                query.setObject(1, OffsetDateTime.now());
                query.setBigDecimal(2, new BigDecimal("50.00"));
                query.setString(3, "%@example.com");
                try (ResultSet rows = query.executeQuery()) {
                    ResultSetMetaData columns = rows.getMetaData();
                    assertThat(columns.getColumnCount()).isEqualTo(6);

                    assertThat(columns.getColumnName(1)).isEqualTo("email");
                    assertThat(columns.getTableName(1)).isEqualTo("members");
                    assertThat(columns.getSchemaName(1)).isEmpty();
                    PGResultSetMetaData origin = columns.unwrap(PGResultSetMetaData.class);
                    assertThat(origin.getBaseSchemaName(1)).isEqualTo(SCHEMA);
                    assertThat(origin.getBaseTableName(2)).isEqualTo("books");
                    assertThat(origin.getBaseColumnName(2)).isEqualTo("title");
                    assertThat(origin.getFormat(3)).isEqualTo(0);
                    assertThat(columns.getColumnType(1)).isEqualTo(Types.VARCHAR);
                    assertThat(columns.getPrecision(1)).isEqualTo(120);
                    assertThat(columns.isNullable(1)).isEqualTo(ResultSetMetaData.columnNoNulls);

                    assertThat(columns.getColumnLabel(2)).isEqualTo("book_title");
                    assertThat(columns.getColumnName(2)).isEqualTo("book_title");
                    assertThat(columns.getTableName(2)).isEqualTo("books");

                    assertThat(columns.getColumnType(3)).isEqualTo(Types.NUMERIC);
                    assertThat(columns.getPrecision(3)).isEqualTo(8);
                    assertThat(columns.getScale(3)).isEqualTo(2);
                    assertThat(columns.isNullable(3)).isEqualTo(ResultSetMetaData.columnNullable);
                    assertThat(columns.getColumnClassName(3)).isEqualTo(BigDecimal.class.getName());

                    assertThat(columns.getColumnType(4)).isEqualTo(Types.TIMESTAMP);
                    assertThat(columns.getColumnTypeName(4)).isEqualTo("timestamptz");
                    assertThat(columns.getColumnClassName(4)).isEqualTo(Timestamp.class.getName());

                    assertThat(columns.getColumnType(5)).isEqualTo(Types.BIT);
                    assertThat(columns.getColumnClassName(5)).isEqualTo(Boolean.class.getName());

                    assertThat(columns.getColumnLabel(6)).isEqualTo("total_overdue");
                    assertThat(columns.getColumnType(6)).isEqualTo(Types.BIGINT);
                    assertThat(columns.getTableName(6)).isEmpty();
                    assertThat(columns.isNullable(6)).isEqualTo(ResultSetMetaData.columnNullableUnknown);

                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("email")).isEqualTo("ada@example.com");
                    assertThat(rows.getString("book_title")).isEqualTo("Introduction to Algorithms");
                    assertThat(rows.getBigDecimal("price")).isEqualByComparingTo("94.50");
                    assertThat(rows.getBoolean("available")).isTrue();
                    assertThat(rows.getLong("total_overdue")).isEqualTo(1L);
                    assertThat(rows.next()).isFalse();
                }
            }
        }
    }

    private static List<String> schemas(DatabaseMetaData catalog) throws SQLException {
        List<String> names = new ArrayList<>();
        try (ResultSet found = catalog.getSchemas()) {
            while (found.next()) {
                names.add(found.getString("TABLE_SCHEM"));
            }
        }
        return names;
    }

    private static List<ColumnDescription> columns(DatabaseMetaData catalog, String table) throws SQLException {
        List<ColumnDescription> described = new ArrayList<>();
        try (ResultSet found = catalog.getColumns(null, SCHEMA, table, "%")) {
            while (found.next()) {
                described.add(new ColumnDescription(
                        found.getString("COLUMN_NAME"),
                        found.getInt("DATA_TYPE"),
                        found.getString("TYPE_NAME"),
                        found.getInt("COLUMN_SIZE"),
                        found.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                        "YES".equals(found.getString("IS_AUTOINCREMENT"))));
            }
        }
        return described;
    }

    private static List<String> primaryKey(DatabaseMetaData catalog, String table) throws SQLException {
        List<String> keyColumns = new ArrayList<>();
        try (ResultSet found = catalog.getPrimaryKeys(null, SCHEMA, table)) {
            while (found.next()) {
                keyColumns.add(found.getString("COLUMN_NAME"));
            }
        }
        return keyColumns;
    }

    private static ForeignKey foreignKey(ResultSet row) throws SQLException {
        return new ForeignKey(
                row.getString("FK_NAME"),
                row.getString("FKTABLE_NAME"),
                row.getString("FKCOLUMN_NAME"),
                row.getString("PKTABLE_NAME"),
                row.getString("PKCOLUMN_NAME"),
                row.getInt("DELETE_RULE"));
    }

    /// Maps each index name of `table` to whether it allows duplicate values.
    private static Map<String, Boolean> indexes(DatabaseMetaData catalog, String table, boolean uniqueOnly)
            throws SQLException {
        Map<String, Boolean> found = new LinkedHashMap<>();
        try (ResultSet info = catalog.getIndexInfo(null, SCHEMA, table, uniqueOnly, false)) {
            while (info.next()) {
                found.put(info.getString("INDEX_NAME"), info.getBoolean("NON_UNIQUE"));
            }
        }
        return found;
    }

    private void awaitDatabase() throws Exception {
        long deadline = System.currentTimeMillis() + 120_000L;
        SQLException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection probe = dataSource.getConnection(); Statement statement = probe.createStatement()) {
                statement.execute("SELECT 1");
                return;
            } catch (SQLException e) {
                lastFailure = e;
                Thread.sleep(500L);
            }
        }
        throw new IllegalStateException("PostgreSQL did not accept connections in " + containerName, lastFailure);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
