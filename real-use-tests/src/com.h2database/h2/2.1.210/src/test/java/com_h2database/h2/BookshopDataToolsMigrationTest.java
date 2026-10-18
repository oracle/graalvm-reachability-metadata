/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_h2database.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.h2.tools.Backup;
import org.h2.tools.Csv;
import org.h2.tools.DeleteDbFiles;
import org.h2.tools.Restore;
import org.h2.tools.RunScript;
import org.h2.tools.Script;
import org.h2.tools.SimpleResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The data import, export, and maintenance tools in `org.h2.tools`: `Csv` reading and writing,
 * `SimpleResultSet`, the `CSVREAD` and `CSVWRITE` SQL functions, `Script` and `RunScript` SQL
 * dumps, and `Backup`, `Restore`, and `DeleteDbFiles` on database files.
 *
 * A bookshop imports a supplier price list delivered as semicolon-separated CSV, publishes a
 * restock report as CSV, migrates its catalog to a fresh database through an SQL dump, and
 * restores its file database from a zip backup after the files were deleted.
 */
public class BookshopDataToolsMigrationTest {

    private static final String SUPPLIER_FEED = """
            # supplier feed, week 42
            ISBN;TITLE;PRICE;STOCK
            978-0134685991;"Effective Java; 3rd Edition";45.99;12
            978-1617294945;"Kotlin in Action";39.50;0
            978-0596009205;"Head First ""Design"" Patterns";52.00;3
            978-1491950357;;29.90;
            """;

    private static final String CATALOG_SCHEMA = """
            CREATE TABLE book (
                isbn VARCHAR(20) PRIMARY KEY,
                title VARCHAR(120),
                price DECIMAL(8, 2) NOT NULL,
                stock INT
            );
            """;

    @TempDir
    Path directory;

    @Test
    void readsSupplierFeedWithCustomSeparatorQuotesAndNulls() throws IOException, SQLException {
        Csv csv = new Csv();
        String charset = csv.setOptions("fieldSeparator=; lineComment=# null=");
        assertEquals(null, charset);
        assertEquals(';', csv.getFieldSeparatorRead());
        assertEquals('#', csv.getLineCommentCharacter());

        List<Map<String, String>> rows = new ArrayList<>();
        try (ResultSet feed = csv.read(new StringReader(SUPPLIER_FEED), null)) {
            ResultSetMetaData meta = feed.getMetaData();
            assertEquals(4, meta.getColumnCount());
            assertEquals("ISBN", meta.getColumnLabel(1));
            assertEquals("STOCK", meta.getColumnLabel(4));
            while (feed.next()) {
                Map<String, String> row = new LinkedHashMap<>();
                for (int column = 1; column <= meta.getColumnCount(); column++) {
                    row.put(meta.getColumnLabel(column), feed.getString(column));
                }
                rows.add(row);
            }
        }

        assertEquals(4, rows.size());
        assertEquals("Effective Java; 3rd Edition", rows.get(0).get("TITLE"));
        assertEquals("45.99", rows.get(0).get("PRICE"));
        assertEquals("Head First \"Design\" Patterns", rows.get(2).get("TITLE"));
        assertEquals("0", rows.get(1).get("STOCK"));
        assertEquals(null, rows.get(3).get("TITLE"));
        assertEquals(null, rows.get(3).get("STOCK"));
    }

    @Test
    void importsFeedWithCsvReadAndPublishesReportWithCsvWrite() throws IOException, SQLException {
        Path feedFile = directory.resolve("supplier-feed.csv");
        Files.writeString(feedFile, SUPPLIER_FEED, StandardCharsets.UTF_8);
        Path reportFile = directory.resolve("restock-report.csv");

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:csv-import", "sa", "");
                Statement statement = connection.createStatement()) {
            statement.execute(CATALOG_SCHEMA);
            int imported = statement.executeUpdate("""
                    INSERT INTO book(isbn, title, price, stock)
                    SELECT ISBN, TITLE, CAST(PRICE AS DECIMAL(8, 2)), CAST(STOCK AS INT)
                    FROM CSVREAD('%s', NULL, 'charset=UTF-8 fieldSeparator=; lineComment=# null=')
                    """.formatted(feedFile));
            assertEquals(4, imported);

            try (ResultSet total = statement.executeQuery("SELECT SUM(price * stock) FROM book")) {
                assertTrue(total.next());
                assertEquals(new BigDecimal("707.88"), total.getBigDecimal(1));
            }

            try (ResultSet written = statement.executeQuery("""
                    CALL CSVWRITE('%s',
                        'SELECT isbn, title, COALESCE(stock, 0) AS on_hand FROM book
                         WHERE COALESCE(stock, 0) < 5 ORDER BY isbn',
                        'charset=UTF-8 fieldSeparator=,')
                    """.formatted(reportFile))) {
                assertTrue(written.next());
                assertEquals(3, written.getInt(1));
            }
        }

        Csv reader = new Csv();
        List<String> lines = new ArrayList<>();
        try (ResultSet report = reader.read(reportFile.toString(), null, "UTF-8")) {
            assertEquals("ON_HAND", report.getMetaData().getColumnLabel(3));
            while (report.next()) {
                lines.add(report.getString("ISBN") + "|" + report.getString("TITLE") + "|"
                        + report.getString("ON_HAND"));
            }
        }
        assertEquals(List.of(
                "978-0596009205|Head First \"Design\" Patterns|3",
                "978-1491950357|null|0",
                "978-1617294945|Kotlin in Action|0"), lines);
    }

    @Test
    void writesInMemorySimpleResultSetAsCsv() throws SQLException {
        SimpleResultSet shelf = new SimpleResultSet();
        shelf.addColumn("SHELF", Types.VARCHAR, 10, 0);
        shelf.addColumn("GENRE", Types.VARCHAR, 40, 0);
        shelf.addColumn("BOOKS", Types.INTEGER, 10, 0);
        shelf.addRow("A1", "Programming, Java", 42);
        shelf.addRow("B7", "Poetry", 7);
        shelf.addRow("C3", null, 0);

        Csv csv = new Csv();
        csv.setLineSeparator("\n");
        csv.setFieldSeparatorWrite(";");
        csv.setNullString("\\N");
        StringWriter out = new StringWriter();
        int written = csv.write(out, shelf);

        assertEquals(3, written);
        assertEquals("""
                "SHELF";"GENRE";"BOOKS"
                "A1";"Programming, Java";"42"
                "B7";"Poetry";"7"
                "C3";\\N;"0"
                """, out.toString());

        Csv parser = new Csv();
        parser.setFieldSeparatorRead(';');
        parser.setNullString("\\N");
        try (ResultSet back = parser.read(new StringReader(out.toString()), null)) {
            assertTrue(back.next());
            assertEquals("Programming, Java", back.getString("GENRE"));
            assertTrue(back.next());
            assertTrue(back.next());
            assertEquals(null, back.getString("GENRE"));
            assertFalse(back.next());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void migratesCatalogThroughSqlDumpIntoFreshDatabase() throws IOException, SQLException {
        Path dump = directory.resolve("catalog-dump.sql");
        try (Connection source = DriverManager.getConnection("jdbc:h2:mem:catalog-source", "sa", "");
                Statement statement = source.createStatement()) {
            statement.execute(CATALOG_SCHEMA);
            statement.execute("""
                    CREATE INDEX book_title_idx ON book(title);
                    CREATE SEQUENCE order_seq START WITH 1000;
                    INSERT INTO book VALUES
                        ('978-0134685991', 'Effective Java', 45.99, 12),
                        ('978-1617294945', 'Kotlin in Action', 39.50, 0);
                    """);
            Script.process(source, dump.toString(), "", "CHARSET 'UTF-8'");
        }

        String sql = Files.readString(dump, StandardCharsets.UTF_8);
        assertTrue(sql.contains("CREATE MEMORY TABLE \"PUBLIC\".\"BOOK\""), sql);
        assertTrue(sql.contains("CREATE INDEX \"PUBLIC\".\"BOOK_TITLE_IDX\""), sql);
        assertTrue(sql.contains("'Kotlin in Action'"), sql);

        try (Connection target = DriverManager.getConnection("jdbc:h2:mem:catalog-target", "sa", "")) {
            try (Reader script = Files.newBufferedReader(dump, StandardCharsets.UTF_8)) {
                RunScript.execute(target, script);
            }
            try (Statement statement = target.createStatement()) {
                try (ResultSet books = statement.executeQuery(
                        "SELECT isbn, price, stock FROM book ORDER BY isbn")) {
                    assertTrue(books.next());
                    assertEquals("978-0134685991", books.getString(1));
                    assertEquals(new BigDecimal("45.99"), books.getBigDecimal(2));
                    assertEquals(12, books.getInt(3));
                    assertTrue(books.next());
                    assertEquals(0, books.getInt(3));
                    assertFalse(books.next());
                }
                try (ResultSet next = statement.executeQuery("VALUES NEXT VALUE FOR order_seq")) {
                    assertTrue(next.next());
                    assertEquals(1000L, next.getLong(1));
                }
                try (ResultSet index = statement.executeQuery("""
                        SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES
                        WHERE INDEX_NAME = 'BOOK_TITLE_IDX'
                        """)) {
                    assertTrue(index.next());
                    assertEquals(1, index.getInt(1));
                }
            }
        }
    }

    @Test
    void restoresDeletedFileDatabaseFromZipBackup() throws SQLException {
        String dir = directory.toString();
        String url = "jdbc:h2:" + directory.resolve("shop").toAbsolutePath();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.execute(CATALOG_SCHEMA);
            statement.execute("INSERT INTO book VALUES ('978-0596009205', 'Head First Design Patterns', 52.00, 3)");
        }
        Path databaseFile = directory.resolve("shop.mv.db");
        assertTrue(Files.exists(databaseFile));

        String zip = directory.resolve("shop-backup.zip").toString();
        Backup.execute(zip, dir, "shop", true);
        assertTrue(Files.exists(Path.of(zip)));

        DeleteDbFiles.execute(dir, "shop", true);
        assertFalse(Files.exists(databaseFile));

        Restore.execute(zip, dir, "shop");
        assertTrue(Files.exists(databaseFile));

        try (Connection connection = DriverManager.getConnection(url + ";IFEXISTS=TRUE", "sa", "");
                Statement statement = connection.createStatement();
                ResultSet book = statement.executeQuery("SELECT title, stock FROM book")) {
            assertTrue(book.next());
            assertEquals("Head First Design Patterns", book.getString(1));
            assertEquals(3, book.getInt(2));
            assertFalse(book.next());
        }
    }
}
