/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_h2database.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.h2.api.ErrorCode;
import org.h2.tools.ChangeFileEncryption;
import org.h2.tools.DeleteDbFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Database storage on H2's pluggable file system: AES-encrypted database files opened with
 * `CIPHER=AES` and re-keyed or decrypted with `org.h2.tools.ChangeFileEncryption`, the `memFS:`
 * and `nioMemFS:` in-memory file systems, `split:` files cut into fixed-size parts, the `nio:`
 * channel file system, read-only databases inside a zip archive through `zip:`, and
 * `ACCESS_MODE_DATA=r`.
 *
 * A records archive keeps its sensitive ledger encrypted, rotates and finally removes the file
 * key, stages imports in memory-backed file systems, stores bulk history in split files, and
 * publishes a frozen archive as a zip that readers can query but never modify.
 */
public class ArchiveStorageFileSystemsTest {

    private static final String SCHEMA = """
            CREATE TABLE record (
                id INT PRIMARY KEY,
                title VARCHAR(80) NOT NULL,
                body VARCHAR(4000)
            )
            """;

    @TempDir
    Path directory;

    @Test
    void encryptedLedgerIsRekeyedAndDecryptedWithChangeFileEncryption() throws SQLException {
        String base = directory.resolve("ledger").toString();
        String encryptedUrl = "jdbc:h2:" + base + ";CIPHER=AES";

        try (Connection connection = DriverManager.getConnection(encryptedUrl, "sa", "vault-1 user-pw")) {
            createRecords(connection, 3);
        }
        Path file = directory.resolve("ledger.mv.db");
        assertTrue(Files.exists(file));

        SQLException wrongKey = assertThrows(SQLException.class,
                () -> DriverManager.getConnection(encryptedUrl, "sa", "vault-x user-pw").close());
        assertEquals(ErrorCode.FILE_ENCRYPTION_ERROR_1, wrongKey.getErrorCode());
        SQLException noCipher = assertThrows(SQLException.class,
                () -> DriverManager.getConnection("jdbc:h2:" + base, "sa", "user-pw").close());
        assertEquals(ErrorCode.FILE_CORRUPTED_1, noCipher.getErrorCode());

        ChangeFileEncryption.execute(directory.toString(), "ledger", "AES",
                "vault-1".toCharArray(), "vault-2".toCharArray(), true);
        assertThrows(SQLException.class,
                () -> DriverManager.getConnection(encryptedUrl, "sa", "vault-1 user-pw").close());
        try (Connection connection = DriverManager.getConnection(encryptedUrl, "sa", "vault-2 user-pw")) {
            assertEquals(List.of("record-1", "record-2", "record-3"), titles(connection));
            insertRecord(connection, 4, "record-4");
        }

        ChangeFileEncryption.execute(directory.toString(), "ledger", "AES",
                "vault-2".toCharArray(), null, true);
        try (Connection connection = DriverManager.getConnection("jdbc:h2:" + base, "sa", "user-pw")) {
            assertEquals(List.of("record-1", "record-2", "record-3", "record-4"), titles(connection));
        }
    }

    @Test
    void memoryFileSystemsKeepDatabasesAfterTheLastConnectionCloses() throws SQLException {
        for (String prefix : List.of("memFS:", "nioMemFS:")) {
            String name = "staging-" + UUID.randomUUID();
            String url = "jdbc:h2:" + prefix + name;

            try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
                createRecords(connection, 2);
            }
            try (Connection connection = DriverManager.getConnection(url + ";IFEXISTS=TRUE", "sa", "")) {
                assertEquals(List.of("record-1", "record-2"), titles(connection), prefix);
            }

            DeleteDbFiles.execute(prefix, name, true);
            SQLException missing = assertThrows(SQLException.class,
                    () -> DriverManager.getConnection(url + ";IFEXISTS=TRUE", "sa", "").close());
            assertEquals(ErrorCode.DATABASE_NOT_FOUND_WITH_IF_EXISTS_1, missing.getErrorCode(), prefix);
        }
    }

    @Test
    void splitFileSystemCutsHistoryIntoPartsThatReopenAsOneDatabase() throws SQLException, IOException {
        Path history = Files.createDirectory(directory.resolve("history"));
        String url = "jdbc:h2:split:16:" + history.resolve("events");

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            createRecords(connection, 0);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO record (id, title, body) VALUES (?, ?, ?)")) {
                for (int id = 1; id <= 400; id++) {
                    insert.setInt(1, id);
                    insert.setString(2, "event-" + id);
                    insert.setString(3, (id + "-" + UUID.nameUUIDFromBytes(new byte[] {(byte) id})).repeat(20));
                    insert.addBatch();
                }
                insert.executeBatch();
            }
        }

        List<String> files;
        try (Stream<Path> listing = Files.list(history)) {
            files = listing.map(path -> path.getFileName().toString()).sorted().collect(Collectors.toList());
        }
        assertTrue(files.contains("events.mv.db"), files.toString());
        assertTrue(files.contains("events.mv.db.1.part"), files.toString());
        assertTrue(Files.size(history.resolve("events.mv.db")) <= 1 << 16);

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT COUNT(*), MAX(title), SUM(LENGTH(body)) > 400 * 600 FROM record")) {
            assertTrue(result.next());
            assertEquals(400, result.getInt(1));
            assertEquals("event-99", result.getString(2));
            assertTrue(result.getBoolean(3));
        }
    }

    @Test
    void zippedArchiveAndReadOnlyAccessModeRejectWrites() throws SQLException, IOException {
        String base = directory.resolve("frozen").toString();
        try (Connection connection = DriverManager.getConnection("jdbc:h2:nio:" + base, "sa", "")) {
            createRecords(connection, 3);
        }

        Path archive = directory.resolve("frozen.zip");
        try (OutputStream out = Files.newOutputStream(archive); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("frozen.mv.db"));
            Files.copy(directory.resolve("frozen.mv.db"), zip);
            zip.closeEntry();
        }

        String zipUrl = "jdbc:h2:zip:" + archive + "!/frozen";
        try (Connection connection = DriverManager.getConnection(zipUrl, "sa", "")) {
            assertReadOnlyArchive(connection);
        }

        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:" + base + ";ACCESS_MODE_DATA=r", "sa", "")) {
            assertReadOnlyArchive(connection);
        }

        try (Connection connection = DriverManager.getConnection("jdbc:h2:" + base, "sa", "")) {
            assertFalse(readOnly(connection));
            insertRecord(connection, 4, "record-4");
            assertEquals(4, titles(connection).size());
        }
    }

    private static void assertReadOnlyArchive(Connection connection) throws SQLException {
        assertTrue(readOnly(connection));
        assertEquals(List.of("record-1", "record-2", "record-3"), titles(connection));
        SQLException rejected = assertThrows(SQLException.class, () -> insertRecord(connection, 9, "late"));
        assertEquals(ErrorCode.DATABASE_IS_READ_ONLY, rejected.getErrorCode());
    }

    private static void createRecords(Connection connection, int count) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(SCHEMA);
        }
        for (int id = 1; id <= count; id++) {
            insertRecord(connection, id, "record-" + id);
        }
    }

    private static void insertRecord(Connection connection, int id, String title) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO record (id, title, body) VALUES (?, ?, ?)")) {
            insert.setInt(1, id);
            insert.setString(2, title);
            insert.setString(3, "body of " + title);
            insert.executeUpdate();
        }
    }

    private static List<String> titles(Connection connection) throws SQLException {
        List<String> titles = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT title FROM record ORDER BY id")) {
            while (result.next()) {
                titles.add(result.getString(1));
            }
        }
        return titles;
    }

    private static boolean readOnly(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT READONLY()")) {
            assertTrue(result.next());
            return result.getBoolean(1);
        }
    }
}
