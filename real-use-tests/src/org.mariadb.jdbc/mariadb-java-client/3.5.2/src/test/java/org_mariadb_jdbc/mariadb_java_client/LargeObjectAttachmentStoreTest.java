/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mariadb_jdbc.mariadb_java_client;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/// Large object storage and streaming: `Blob` and `Clob` values, binary and
/// character streams written and read through prepared statements, partial reads,
/// in-place LOB edits, and SQL `NULL` LOB columns.
///
/// The scenario is a document attachment store behind a HikariCP pool that keeps
/// file contents in `MEDIUMBLOB` columns and their extracted text in `LONGTEXT`.
public class LargeObjectAttachmentStoreTest {

    private static final String IMAGE = "mariadb:13.0";

    private static final String CONTAINER = "real-use-mariadb-lobs-" + UUID.randomUUID();

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);

    private static final String DATABASE = "documents";

    private static final String USER = "documents_app";

    private static final String PASSWORD = "documents-secret";

    private static final int LARGE_SIZE = 1_500_000;

    private static HikariDataSource pool;

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
        String url = "jdbc:mariadb://127.0.0.1:" + port + "/" + DATABASE;
        awaitServer(url);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(USER);
        config.setPassword(PASSWORD);
        config.setMaximumPoolSize(2);
        config.setPoolName("attachments");
        pool = new HikariDataSource(config);

        try (Connection connection = pool.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE attachments (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        file_name VARCHAR(200) NOT NULL,
                        content MEDIUMBLOB,
                        extracted_text LONGTEXT
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");
        }
    }

    @AfterAll
    static void stopServer() throws Exception {
        try {
            if (pool != null) {
                pool.close();
            }
        } finally {
            docker("rm", "-f", "-v", CONTAINER);
        }
    }

    @Test
    void largeBinaryStreamRoundTripsIntact() throws Exception {
        AttachmentStore store = new AttachmentStore(pool);
        byte[] archive = deterministicBytes(LARGE_SIZE, 7);

        int id = store.upload("backup.tar", new ByteArrayInputStream(archive), archive.length, null);

        assertThat(store.contentLength(id)).isEqualTo(LARGE_SIZE);
        assertThat(store.downloadDigest(id)).isEqualTo(sha256(archive));
    }

    @Test
    void blobSupportsPartialReadsAndInPlaceEdits() throws Exception {
        AttachmentStore store = new AttachmentStore(pool);
        byte[] header = "%PDF-1.7 draft".getBytes(StandardCharsets.US_ASCII);
        int id = store.uploadBlob("report.pdf", header);

        assertThat(store.readRange(id, 1, 8)).isEqualTo("%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
        assertThat(store.readRange(id, 10, 5)).isEqualTo("draft".getBytes(StandardCharsets.US_ASCII));

        store.overwrite(id, 10, "final".getBytes(StandardCharsets.US_ASCII));

        assertThat(store.readRange(id, 1, header.length))
                .isEqualTo("%PDF-1.7 final".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void characterStreamPreservesMultilingualText() throws Exception {
        AttachmentStore store = new AttachmentStore(pool);
        String paragraph = "Gr\u00fc\u00dfe aus Z\u00fcrich \u2013 \u65e5\u672c\u8a9e "
                + "\u0645\u0631\u062d\u0628\u0627 \ud83d\udcc4\n";
        String text = paragraph.repeat(20_000);

        int id = store.upload("letters.txt", new ByteArrayInputStream(new byte[] {1, 2, 3}), 3, new StringReader(text));

        assertThat(store.extractedText(id)).isEqualTo(text);
        assertThat(store.textPrefix(id, paragraph.length())).isEqualTo(paragraph);
        assertThat(store.searchText("Z\u00fcrich")).contains("letters.txt");
    }

    @Test
    void clobWrittenThroughWriterIsStored() throws Exception {
        AttachmentStore store = new AttachmentStore(pool);

        int id = store.uploadNotes("minutes.md", "# Minutes\n\n- budget approved\n- next review in May\n");

        String stored = store.extractedText(id);
        assertThat(stored).startsWith("# Minutes").contains("budget approved").endsWith("May\n");
        assertThat(store.textPrefix(id, 9)).isEqualTo("# Minutes");
    }

    @Test
    void missingContentIsSqlNull() throws Exception {
        AttachmentStore store = new AttachmentStore(pool);

        int id = store.placeholder("pending-scan.png");

        assertThat(store.content(id)).isEmpty();
        assertThat(store.extractedText(id)).isNull();
    }

    /// The application's attachment repository, reading and writing LOB columns.
    static final class AttachmentStore {

        private final HikariDataSource dataSource;

        AttachmentStore(HikariDataSource dataSource) {
            this.dataSource = dataSource;
        }

        int upload(String fileName, InputStream content, long length, Reader text) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO attachments (file_name, content, extracted_text) VALUES (?, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, fileName);
                insert.setBinaryStream(2, content, length);
                if (text == null) {
                    insert.setNull(3, Types.LONGVARCHAR);
                } else {
                    insert.setCharacterStream(3, text);
                }
                insert.executeUpdate();
                return generatedId(insert);
            }
        }

        int uploadBlob(String fileName, byte[] content) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO attachments (file_name, content) VALUES (?, ?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                Blob blob = connection.createBlob();
                assertThat(blob.setBytes(1, content)).isEqualTo(content.length);
                insert.setString(1, fileName);
                insert.setBlob(2, blob);
                insert.executeUpdate();
                return generatedId(insert);
            }
        }

        int uploadNotes(String fileName, String notes) throws SQLException, IOException {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO attachments (file_name, extracted_text) VALUES (?, ?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                Clob clob = connection.createClob();
                try (Writer writer = clob.setCharacterStream(1)) {
                    writer.write(notes);
                }
                assertThat(clob.length()).isEqualTo(notes.length());
                insert.setString(1, fileName);
                insert.setClob(2, clob);
                insert.executeUpdate();
                return generatedId(insert);
            }
        }

        int placeholder(String fileName) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO attachments (file_name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, fileName);
                insert.executeUpdate();
                return generatedId(insert);
            }
        }

        long contentLength(int id) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement query = connection.prepareStatement(
                            "SELECT content FROM attachments WHERE id = ?")) {
                query.setInt(1, id);
                try (ResultSet rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    return rows.getBlob(1).length();
                }
            }
        }

        String downloadDigest(int id) throws SQLException, IOException {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement query = connection.prepareStatement(
                            "SELECT content FROM attachments WHERE id = ?")) {
                query.setInt(1, id);
                try (ResultSet rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    MessageDigest digest = newDigest();
                    try (InputStream stream = rows.getBinaryStream("content")) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = stream.read(buffer)) != -1) {
                            digest.update(buffer, 0, read);
                        }
                    }
                    return HexFormat.of().formatHex(digest.digest());
                }
            }
        }

        byte[] readRange(int id, long position, int length) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement query = connection.prepareStatement(
                            "SELECT content FROM attachments WHERE id = ?")) {
                query.setInt(1, id);
                try (ResultSet rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    return rows.getBlob(1).getBytes(position, length);
                }
            }
        }

        void overwrite(int id, long position, byte[] replacement) throws SQLException {
            try (Connection connection = dataSource.getConnection()) {
                Blob blob;
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT content FROM attachments WHERE id = ?")) {
                    query.setInt(1, id);
                    try (ResultSet rows = query.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                        blob = rows.getBlob(1);
                    }
                }
                blob.setBytes(position, replacement);
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE attachments SET content = ? WHERE id = ?")) {
                    update.setBlob(1, blob);
                    update.setInt(2, id);
                    assertThat(update.executeUpdate()).isEqualTo(1);
                }
            }
        }

        Optional<byte[]> content(int id) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement query = connection.prepareStatement(
                            "SELECT content FROM attachments WHERE id = ?")) {
                query.setInt(1, id);
                try (ResultSet rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    Blob blob = rows.getBlob(1);
                    assertThat(rows.wasNull()).isEqualTo(blob == null);
                    return blob == null ? Optional.empty() : Optional.of(blob.getBytes(1, (int) blob.length()));
                }
            }
        }

        String extractedText(int id) throws SQLException, IOException {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement query = connection.prepareStatement(
                            "SELECT extracted_text FROM attachments WHERE id = ?")) {
                query.setInt(1, id);
                try (ResultSet rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    try (Reader reader = rows.getCharacterStream(1)) {
                        if (reader == null) {
                            return null;
                        }
                        StringBuilder text = new StringBuilder();
                        char[] buffer = new char[4096];
                        int read;
                        while ((read = reader.read(buffer)) != -1) {
                            text.append(buffer, 0, read);
                        }
                        return text.toString();
                    }
                }
            }
        }

        String textPrefix(int id, int length) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement query = connection.prepareStatement(
                            "SELECT extracted_text FROM attachments WHERE id = ?")) {
                query.setInt(1, id);
                try (ResultSet rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    Clob clob = rows.getClob(1);
                    return clob.getSubString(1, length);
                }
            }
        }

        List<String> searchText(String needle) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement query = connection.prepareStatement(
                            "SELECT file_name FROM attachments WHERE extracted_text LIKE ? ORDER BY id")) {
                query.setString(1, "%" + needle + "%");
                List<String> names = new ArrayList<>();
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        names.add(rows.getString(1));
                    }
                }
                return names;
            }
        }

        private static int generatedId(PreparedStatement insert) throws SQLException {
            try (ResultSet keys = insert.getGeneratedKeys()) {
                assertThat(keys.next()).isTrue();
                return keys.getInt(1);
            }
        }
    }

    private static byte[] deterministicBytes(int size, int seed) {
        byte[] bytes = new byte[size];
        int state = seed;
        for (int i = 0; i < size; i++) {
            state = state * 1_103_515_245 + 12_345;
            bytes[i] = (byte) (state >>> 16);
        }
        return bytes;
    }

    private static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(newDigest().digest(bytes));
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void awaitServer(String url) throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        SQLException last = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = DriverManager.getConnection(url, USER, PASSWORD)) {
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
