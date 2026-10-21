/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_postgresql.postgresql;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.postgresql.ds.PGSimpleDataSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Maintains a library shelf through pgjdbc's scrollable, updatable and cursor-fetched result sets.
 *
 * A librarian browses the shelf with a `TYPE_SCROLL_INSENSITIVE` result set, jumping to absolute
 * and relative rows; edits it in place through a `CONCUR_UPDATABLE` result set that updates,
 * inserts and deletes rows which are then visible to a fresh query; and walks a large stock
 * listing through a forward-only result set with a fetch size, which pgjdbc backs with a
 * server-side portal while auto-commit is off.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LibraryShelfUpdatableResultSetTest {

    private static final String POSTGRES_IMAGE = "postgres:18-alpine";

    private static final String DATABASE = "library";

    private static final String USER = "librarian";

    private static final String PASSWORD = "s3cret";

    private static final int STOCK_ROWS = 230;

    private final String containerName = "real-use-postgresql-" + UUID.randomUUID();

    private PGSimpleDataSource dataSource;

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
            statement.execute("""
                    CREATE TABLE books (
                        isbn    text          PRIMARY KEY,
                        title   text          NOT NULL,
                        author  text          NOT NULL,
                        copies  integer       NOT NULL,
                        price   numeric(8, 2) NOT NULL
                    )""");
            statement.execute("""
                    CREATE TABLE stock_movements (
                        id        integer PRIMARY KEY,
                        shelf     text    NOT NULL,
                        quantity  integer NOT NULL
                    )""");
            statement.execute("""
                    INSERT INTO stock_movements (id, shelf, quantity)
                    SELECT n, 'shelf-' || (n %% 7), n * 3
                    FROM generate_series(1, %d) AS n""".formatted(STOCK_ROWS));
        }
    }

    @AfterAll
    void stopDatabase() throws Exception {
        Process remove = new ProcessBuilder("docker", "rm", "-f", containerName).inheritIO().start();
        remove.waitFor(60, TimeUnit.SECONDS);
    }

    @BeforeEach
    void restockShelf() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE books");
            statement.execute("""
                    INSERT INTO books (isbn, title, author, copies, price) VALUES
                        ('978-0131103627', 'The C Programming Language', 'Kernighan', 3, 54.99),
                        ('978-0201633610', 'Design Patterns', 'Gamma', 2, 49.95),
                        ('978-0262033848', 'Introduction to Algorithms', 'Cormen', 5, 99.00),
                        ('978-0321125217', 'Domain-Driven Design', 'Evans', 1, 62.50),
                        ('978-0596007126', 'Head First Design Patterns', 'Freeman', 4, 44.10)""");
        }
    }

    @Test
    void librarianBrowsesShelfWithScrollableCursor() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement(
                        ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                ResultSet shelf = statement.executeQuery("SELECT isbn, title, author FROM books ORDER BY title")) {
            assertThat(shelf.getType()).isEqualTo(ResultSet.TYPE_SCROLL_INSENSITIVE);
            assertThat(shelf.isBeforeFirst()).isTrue();

            assertThat(shelf.last()).isTrue();
            assertThat(shelf.getRow()).isEqualTo(5);
            assertThat(shelf.getString("title")).isEqualTo("The C Programming Language");

            assertThat(shelf.absolute(2)).isTrue();
            assertThat(shelf.getString("title")).isEqualTo("Domain-Driven Design");

            assertThat(shelf.relative(2)).isTrue();
            assertThat(shelf.getString("title")).isEqualTo("Introduction to Algorithms");

            assertThat(shelf.previous()).isTrue();
            assertThat(shelf.getString("isbn")).isEqualTo("978-0596007126");

            assertThat(shelf.absolute(-1)).isTrue();
            assertThat(shelf.getRow()).isEqualTo(5);

            assertThat(shelf.first()).isTrue();
            assertThat(shelf.getString("title")).isEqualTo("Design Patterns");
            assertThat(shelf.isFirst()).isTrue();

            shelf.afterLast();
            assertThat(shelf.isAfterLast()).isTrue();
            List<String> reversed = new ArrayList<>();
            while (shelf.previous()) {
                reversed.add(shelf.getString("author"));
            }
            assertThat(reversed).containsExactly("Kernighan", "Cormen", "Freeman", "Evans", "Gamma");

            assertThat(shelf.absolute(6)).isFalse();
            assertThat(shelf.isAfterLast()).isTrue();
        }
    }

    @Test
    void librarianEditsShelfInPlaceThroughUpdatableResultSet() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement(
                        ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
                ResultSet shelf = statement.executeQuery(
                        "SELECT isbn, title, author, copies, price FROM books ORDER BY isbn")) {
            assertThat(shelf.getConcurrency()).isEqualTo(ResultSet.CONCUR_UPDATABLE);

            assertThat(shelf.absolute(2)).isTrue();
            assertThat(shelf.getString("title")).isEqualTo("Design Patterns");
            shelf.updateInt("copies", shelf.getInt("copies") + 6);
            shelf.updateBigDecimal("price", new BigDecimal("39.90"));
            shelf.updateRow();
            assertThat(shelf.getInt("copies")).isEqualTo(8);
            assertThat(shelf.getBigDecimal("price")).isEqualByComparingTo("39.90");

            assertThat(shelf.absolute(4)).isTrue();
            assertThat(shelf.getString("author")).isEqualTo("Evans");
            shelf.deleteRow();

            shelf.moveToInsertRow();
            shelf.updateString("isbn", "978-1617294945");
            shelf.updateString("title", "Spring in Action");
            shelf.updateString("author", "Walls");
            shelf.updateInt("copies", 7);
            shelf.updateBigDecimal("price", new BigDecimal("47.99"));
            shelf.insertRow();
            shelf.moveToCurrentRow();

            assertThat(shelf.absolute(1)).isTrue();
            shelf.updateString("title", "The C Programming Language, 2nd Edition");
            shelf.cancelRowUpdates();
            assertThat(shelf.getString("title")).isEqualTo("The C Programming Language");

            shelf.updateInt("copies", 0);
            shelf.updateRow();
            shelf.refreshRow();
            assertThat(shelf.getInt("copies")).isZero();
        }

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet books = statement.executeQuery("SELECT isbn, copies, price FROM books ORDER BY isbn")) {
            List<String> inventory = new ArrayList<>();
            while (books.next()) {
                inventory.add(books.getString("isbn") + "=" + books.getInt("copies") + "@" + books.getBigDecimal("price"));
            }
            assertThat(inventory).containsExactly(
                    "978-0131103627=0@54.99",
                    "978-0201633610=8@39.90",
                    "978-0262033848=5@99.00",
                    "978-0596007126=4@44.10",
                    "978-1617294945=7@47.99");
        }
    }

    @Test
    void stockReportStreamsThroughServerSidePortal() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement report = connection.prepareStatement(
                    "SELECT id, shelf, quantity FROM stock_movements WHERE quantity > ? ORDER BY id")) {
                report.setFetchSize(40);
                report.setInt(1, 0);
                long quantityTotal = 0;
                int rows = 0;
                int previousId = 0;
                try (ResultSet movements = report.executeQuery()) {
                    assertThat(movements.getFetchSize()).isEqualTo(40);
                    while (movements.next()) {
                        int id = movements.getInt("id");
                        assertThat(id).isEqualTo(previousId + 1);
                        assertThat(movements.getString("shelf")).isEqualTo("shelf-" + (id % 7));
                        quantityTotal += movements.getInt("quantity");
                        previousId = id;
                        rows++;
                        if (rows == 1) {
                            assertThat(openPortals(connection)).isPositive();
                        }
                    }
                }
                assertThat(rows).isEqualTo(STOCK_ROWS);
                assertThat(quantityTotal).isEqualTo(3L * STOCK_ROWS * (STOCK_ROWS + 1) / 2);
            }
            connection.commit();
            assertThat(openPortals(connection)).isZero();
        }
    }

    /// Counts the named portals the server holds open for this session; the probe itself runs
    /// in the unnamed portal.
    private static int openPortals(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet cursors = statement.executeQuery("SELECT count(*) FROM pg_cursors WHERE name <> ''")) {
            assertThat(cursors.next()).isTrue();
            return cursors.getInt(1);
        }
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
