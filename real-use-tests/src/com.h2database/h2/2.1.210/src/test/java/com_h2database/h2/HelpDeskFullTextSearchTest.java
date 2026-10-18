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

import java.nio.file.Path;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.h2.fulltext.FullText;
import org.h2.fulltext.FullTextLucene;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Full-text search over table data: the native index of `org.h2.fulltext.FullText` with its
 * `FT_*` SQL functions, ignore list, and whitespace settings, and the Lucene-backed index of
 * `org.h2.fulltext.FullTextLucene` with its `FTL_*` SQL functions and Lucene query syntax.
 *
 * A help desk keeps knowledge-base articles and support tickets in file databases, lets agents
 * search them by keyword, keeps the indexes in step with inserts, updates, deletes, and
 * rollbacks, and finds the same hits again after the database is reopened.
 */
public class HelpDeskFullTextSearchTest {

    private static final String ARTICLES = """
            CREATE TABLE article (
                id INT PRIMARY KEY,
                title VARCHAR(120) NOT NULL,
                body VARCHAR(2000) NOT NULL,
                category VARCHAR(20) NOT NULL
            );
            INSERT INTO article VALUES
                (1, 'Printer paper jam', 'Open the rear tray of the HP-4015 and remove the paper.', 'HARDWARE'),
                (2, 'Replacing the printer toner', 'Shake the new toner cartridge before inserting it.', 'HARDWARE'),
                (3, 'Resetting a VPN password', 'Use the self-service portal to reset the VPN password.', 'ACCESS'),
                (4, 'Wi-Fi drops in meeting rooms', 'Forget the network and join the guest SSID.', 'NETWORK');
            """;

    private static final String TICKETS = """
            CREATE TABLE ticket (
                id INT PRIMARY KEY,
                subject VARCHAR(120) NOT NULL,
                description VARCHAR(2000) NOT NULL
            );
            INSERT INTO ticket VALUES
                (101, 'Printer shows paper jam', 'The third floor printer reports a paper jam every morning.'),
                (102, 'Toner low warning', 'The printer on the second floor asks for new toner.'),
                (103, 'Cannot reach VPN', 'Connection to the VPN gateway times out after login.'),
                (104, 'Printing very slow', 'Large spreadsheets take minutes to print.');
            """;

    @TempDir
    Path workDir;

    @Test
    void nativeIndexAnswersKeywordQueriesAndJoinsBackToRows() throws SQLException {
        try (Connection connection = open("kb-native")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(ARTICLES);
                statement.execute("CREATE ALIAS IF NOT EXISTS FT_INIT FOR 'org.h2.fulltext.FullText.init'");
                statement.execute("CALL FT_INIT()");
                statement.execute("CALL FT_CREATE_INDEX('PUBLIC', 'ARTICLE', 'TITLE,BODY')");
            }

            assertEquals(List.of("\"PUBLIC\".\"ARTICLE\" WHERE \"ID\"=1", "\"PUBLIC\".\"ARTICLE\" WHERE \"ID\"=2"),
                    firstColumn(connection, "SELECT QUERY, SCORE FROM FT_SEARCH(?, 0, 0) ORDER BY QUERY", "printer"));
            assertEquals(List.of("\"PUBLIC\".\"ARTICLE\" WHERE \"ID\"=1"),
                    firstColumn(connection, "SELECT QUERY FROM FT_SEARCH(?, 0, 0)", "Paper JAM"));
            assertEquals(List.of(), firstColumn(connection, "SELECT QUERY FROM FT_SEARCH(?, 0, 0)", "HARDWARE"));

            try (PreparedStatement search = connection.prepareStatement("""
                    SELECT ft."SCHEMA", ft."TABLE", ft.COLUMNS, ft.KEYS, ft.SCORE
                    FROM FT_SEARCH_DATA(?, 0, 0) ft
                    """)) {
                search.setString(1, "VPN password");
                try (ResultSet hits = search.executeQuery()) {
                    assertTrue(hits.next());
                    assertEquals("PUBLIC", hits.getString(1));
                    assertEquals("ARTICLE", hits.getString(2));
                    assertEquals(List.of("ID"), arrayValues(hits.getArray(3)));
                    assertEquals(List.of("3"), arrayValues(hits.getArray(4)));
                    assertEquals(1.0f, hits.getFloat(5));
                    assertFalse(hits.next());
                }
            }

            assertEquals(List.of("Printer paper jam", "Replacing the printer toner"), firstColumn(connection, """
                    SELECT a.title
                    FROM FT_SEARCH_DATA(?, 0, 0) ft
                    JOIN article a ON ft."TABLE" = 'ARTICLE' AND a.id = CAST(ft.KEYS[1] AS INT)
                    ORDER BY a.id
                    """, "printer"));
            List<String> firstPage = keys(FullText.searchData(connection, "printer", 1, 0));
            List<String> secondPage = keys(FullText.searchData(connection, "printer", 1, 1));
            assertEquals(1, firstPage.size());
            assertEquals(1, secondPage.size());
            List<String> pages = new ArrayList<>(firstPage);
            pages.addAll(secondPage);
            pages.sort(null);
            assertEquals(List.of("1", "2"), pages);
        }
    }

    @Test
    void nativeIndexFollowsChangesRollbacksSettingsAndReopen() throws SQLException {
        try (Connection connection = open("kb-native-maintenance")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(ARTICLES);
            }
            FullText.init(connection);
            FullText.setIgnoreList(connection, "THE,A,AND,OF");
            FullText.setWhitespaceChars(connection, " \t\n\r\f,.;:!?()'\"");
            FullText.createIndex(connection, "PUBLIC", "ARTICLE", "TITLE,BODY");

            assertEquals(List.of("1"), keys(FullText.searchData(connection, "HP-4015", 0, 0)));
            assertEquals(List.of(), keys(FullText.searchData(connection, "4015", 0, 0)));
            assertEquals(List.of("4"), keys(FullText.searchData(connection, "wi-fi", 0, 0)));
            assertEquals(List.of(), keys(FullText.searchData(connection, "the", 0, 0)));
            assertEquals(List.of("3"), keys(FullText.searchData(connection, "the VPN portal", 0, 0)));

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO article VALUES (5, 'Docking station firmware',
                            'Update the docking station firmware from the vendor portal.', 'HARDWARE')
                        """);
                statement.executeUpdate("UPDATE article SET body = 'Ask the service desk to unlock it.' WHERE id = 3");
                statement.executeUpdate("DELETE FROM article WHERE id = 4");
            }
            assertEquals(List.of("5"), keys(FullText.searchData(connection, "portal", 0, 0)));
            assertEquals(List.of("3"), keys(FullText.searchData(connection, "unlock", 0, 0)));
            assertEquals(List.of(), keys(FullText.searchData(connection, "SSID", 0, 0)));

            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO article VALUES (6, 'Monitor flickers',
                            'Replace the HDMI cable of the monitor.', 'HARDWARE')
                        """);
            }
            assertEquals(List.of("6"), keys(FullText.searchData(connection, "HDMI", 0, 0)));
            connection.rollback();
            connection.setAutoCommit(true);
            assertEquals(List.of(), keys(FullText.searchData(connection, "HDMI", 0, 0)));
        }

        try (Connection reopened = open("kb-native-maintenance")) {
            assertEquals(List.of("5"), keys(FullText.searchData(reopened, "docking firmware", 0, 0)));
            assertEquals(List.of("1"), keys(FullText.searchData(reopened, "HP-4015", 0, 0)));
            try (Statement statement = reopened.createStatement()) {
                statement.execute("CALL FT_REINDEX()");
            }
            assertEquals(List.of("1", "2"), keys(FullText.searchData(reopened, "printer", 0, 0)));
            FullText.dropIndex(reopened, "PUBLIC", "ARTICLE");
            assertEquals(List.of(), keys(FullText.searchData(reopened, "printer", 0, 0)));
            FullText.dropAll(reopened);
            try (Statement statement = reopened.createStatement();
                    ResultSet schemas = statement.executeQuery(
                            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = 'FT'")) {
                assertTrue(schemas.next());
                assertEquals(0, schemas.getInt(1));
            }
        }
    }

    @Test
    void luceneIndexAnswersFieldBooleanPhraseAndWildcardQueries() throws SQLException {
        try (Connection connection = open("tickets-lucene")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(TICKETS);
                statement.execute("CREATE ALIAS IF NOT EXISTS FTL_INIT FOR 'org.h2.fulltext.FullTextLucene.init'");
                statement.execute("CALL FTL_INIT()");
                statement.execute("CALL FTL_CREATE_INDEX('PUBLIC', 'TICKET', NULL)");
            }

            assertEquals(List.of("101", "102", "104"), luceneKeys(connection, "printer OR print*"));
            assertEquals(List.of("101", "102"), luceneKeys(connection, "printer"));
            assertEquals(List.of("101"), luceneKeys(connection, "SUBJECT:printer"));
            assertEquals(List.of("102"), luceneKeys(connection, "printer AND NOT jam"));
            assertEquals(List.of("101"), luceneKeys(connection, "\"paper jam\""));
            assertEquals(List.of("103"), luceneKeys(connection, "DESCRIPTION:gateway AND SUBJECT:vpn"));
            assertEquals(List.of(), luceneKeys(connection, "SUBJECT:gateway"));

            try (PreparedStatement search = connection.prepareStatement(
                    "SELECT QUERY, SCORE FROM FTL_SEARCH(?, 0, 0)")) {
                search.setString(1, "printer jam");
                try (ResultSet hits = search.executeQuery()) {
                    assertTrue(hits.next());
                    assertEquals("\"PUBLIC\".\"TICKET\" WHERE \"ID\"=101", hits.getString(1));
                    float best = hits.getFloat(2);
                    assertTrue(hits.next());
                    assertEquals("\"PUBLIC\".\"TICKET\" WHERE \"ID\"=102", hits.getString(1));
                    assertTrue(best > hits.getFloat(2));
                    assertTrue(hits.getFloat(2) > 0.0f);
                    assertFalse(hits.next());
                }
            }

            assertEquals(List.of("Cannot reach VPN"), firstColumn(connection, """
                    SELECT t.subject
                    FROM FTL_SEARCH_DATA(?, 0, 0) ft
                    JOIN ticket t ON t.id = CAST(ft.KEYS[1] AS INT)
                    """, "timeout OR \"times out\""));
        }
    }

    @Test
    void luceneIndexFollowsChangesRollbacksAndReopen() throws SQLException {
        try (Connection connection = open("tickets-lucene-maintenance")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(TICKETS);
            }
            FullTextLucene.init(connection);
            FullTextLucene.createIndex(connection, "PUBLIC", "TICKET", "SUBJECT,DESCRIPTION");

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO ticket VALUES (105, 'Badge reader offline',
                            'The badge reader at the loading dock does not respond.')
                        """);
                statement.executeUpdate("UPDATE ticket SET description = 'Toner was replaced by facilities.' "
                        + "WHERE id = 102");
                statement.executeUpdate("DELETE FROM ticket WHERE id = 104");
            }
            assertEquals(List.of("105"), keys(FullTextLucene.searchData(connection, "badge", 0, 0)));
            assertEquals(List.of("102"), keys(FullTextLucene.searchData(connection, "facilities", 0, 0)));
            assertEquals(List.of(), keys(FullTextLucene.searchData(connection, "spreadsheets", 0, 0)));
            assertEquals(List.of("101"), keys(FullTextLucene.searchData(connection, "printer", 0, 0)));

            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO ticket VALUES (106, 'Projector bulb', 'The projector bulb burned out.')
                        """);
            }
            connection.rollback();
            connection.setAutoCommit(true);
            assertEquals(List.of(), keys(FullTextLucene.searchData(connection, "projector", 0, 0)));
        }

        try (Connection reopened = open("tickets-lucene-maintenance")) {
            assertEquals(List.of("105"), keys(FullTextLucene.searchData(reopened, "loading dock", 0, 0)));
            try (Statement statement = reopened.createStatement()) {
                statement.execute("CALL FTL_REINDEX()");
            }
            assertEquals(List.of("103"), keys(FullTextLucene.searchData(reopened, "vpn", 0, 0)));
            FullTextLucene.dropIndex(reopened, "PUBLIC", "TICKET");
            assertEquals(List.of(), keys(FullTextLucene.searchData(reopened, "vpn", 0, 0)));
            FullTextLucene.dropAll(reopened);
        }
    }

    private Connection open(String name) throws SQLException {
        return DriverManager.getConnection("jdbc:h2:" + workDir.resolve(name).toAbsolutePath(), "sa", "");
    }

    private static List<String> firstColumn(Connection connection, String sql, String text) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, text);
            try (ResultSet rows = statement.executeQuery()) {
                List<String> values = new ArrayList<>();
                while (rows.next()) {
                    values.add(rows.getString(1));
                }
                return values;
            }
        }
    }

    private static List<String> luceneKeys(Connection connection, String text) throws SQLException {
        return keys(FullTextLucene.searchData(connection, text, 0, 0));
    }

    private static List<String> keys(ResultSet hits) throws SQLException {
        try (hits) {
            List<String> keys = new ArrayList<>();
            while (hits.next()) {
                List<String> key = arrayValues(hits.getArray("KEYS"));
                assertEquals(1, key.size());
                keys.add(key.get(0));
            }
            keys.sort(null);
            return keys;
        }
    }

    private static List<String> arrayValues(Array array) throws SQLException {
        List<String> values = new ArrayList<>();
        for (Object value : (Object[]) array.getArray()) {
            values.add(String.valueOf(value));
        }
        return values;
    }
}
