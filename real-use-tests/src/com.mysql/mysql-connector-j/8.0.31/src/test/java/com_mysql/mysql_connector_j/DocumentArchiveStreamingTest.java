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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Large objects and result-set streaming: a document archive stores binary scans and text bodies
/// as `LONGBLOB`/`LONGTEXT` through JDBC streams and `Blob`/`Clob`, then exports its contents
/// row by row with a streaming result set and in pages with a server-side cursor.
public class DocumentArchiveStreamingTest {

    private static final String IMAGE = "container-registry.oracle.com/mysql/community-server:26.7.1";
    private static final String DATABASE = "archive";
    private static final String USER = "archivist";
    private static final String PASSWORD = "archivist-secret";
    private static final String CONTAINER = "real-use-mysql-lob-" + UUID.randomUUID();
    private static final int LEDGER_ROWS = 1_200;

    private static MysqlDataSource dataSource;
    private static MysqlDataSource cursorDataSource;

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
        String url = "jdbc:mysql://127.0.0.1:" + port + "/" + DATABASE;
        dataSource = new MysqlDataSource();
        dataSource.setURL(url);
        dataSource.setUser(USER);
        dataSource.setPassword(PASSWORD);
        cursorDataSource = new MysqlDataSource();
        cursorDataSource.setURL(url + "?useCursorFetch=true&defaultFetchSize=100");
        cursorDataSource.setUser(USER);
        cursorDataSource.setPassword(PASSWORD);
        awaitServer(TimeUnit.MINUTES.toMillis(3));
        createSchema();
    }

    @AfterAll
    static void stopMysql() throws Exception {
        docker("rm", "-f", CONTAINER);
    }

    @Test
    void storesAndReadsBinaryScansThroughStreamsAndBlobs() throws Exception {
        byte[] scan = scanBytes(3 * 1024 * 1024 + 17);
        byte[] marker = "PAGE-0042".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(marker, 0, scan, 1_000_000, marker.length);
        byte[] thumbnail = scanBytes(4_096);

        long scanId;
        long thumbnailId;
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO document (title, scan) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, "contract-scan.tiff");
                insert.setBinaryStream(2, new ByteArrayInputStream(scan), scan.length);
                assertThat(insert.executeUpdate()).isEqualTo(1);
                scanId = generatedKey(insert);

                Blob blob = connection.createBlob();
                assertThat(blob.setBytes(1, thumbnail)).isEqualTo(thumbnail.length);
                insert.setString(1, "contract-thumb.png");
                insert.setBlob(2, blob);
                assertThat(insert.executeUpdate()).isEqualTo(1);
                thumbnailId = generatedKey(insert);
                blob.free();
            }

            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT title, scan, LENGTH(scan) AS scan_bytes FROM document WHERE id = ?")) {
                select.setLong(1, scanId);
                try (ResultSet row = select.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getString("title")).isEqualTo("contract-scan.tiff");
                    assertThat(row.getLong("scan_bytes")).isEqualTo(scan.length);

                    Blob stored = row.getBlob("scan");
                    assertThat(stored.length()).isEqualTo(scan.length);
                    assertThat(stored.getBytes(1_000_001, marker.length)).isEqualTo(marker);
                    try (InputStream content = stored.getBinaryStream()) {
                        assertThat(sha256(content)).isEqualTo(sha256(new ByteArrayInputStream(scan)));
                    }
                }

                select.setLong(1, thumbnailId);
                try (ResultSet row = select.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    try (InputStream content = row.getBinaryStream("scan")) {
                        assertThat(content.readAllBytes()).isEqualTo(thumbnail);
                    }
                }
            }
        }
    }

    @Test
    void storesAndReadsTextBodiesThroughReadersAndClobs() throws Exception {
        String paragraph = "Clause \u00a75 \u2013 Z\u00fcrich \u65e5\u672c \ud83d\udcc4 applies to every party.\n";
        String body = paragraph.repeat(20_000);

        long bodyId;
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO document (title, body) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, "contract.txt");
                insert.setCharacterStream(2, new StringReader(body), body.length());
                assertThat(insert.executeUpdate()).isEqualTo(1);
                bodyId = generatedKey(insert);

                Clob note = connection.createClob();
                note.setString(1, "Reviewed by legal");
                note.setString(note.length() + 1, " \u2713");
                insert.setString(1, "review-note.txt");
                insert.setClob(2, note);
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }

            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT body, CHAR_LENGTH(body) AS characters FROM document WHERE id = ?")) {
                select.setLong(1, bodyId);
                try (ResultSet row = select.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getLong("characters")).isEqualTo(body.codePointCount(0, body.length()));

                    Clob stored = row.getClob("body");
                    assertThat(stored.length()).isEqualTo(body.length());
                    assertThat(stored.getSubString(1, paragraph.length())).isEqualTo(paragraph);
                    assertThat(stored.position("Z\u00fcrich", 1)).isEqualTo(paragraph.indexOf("Z\u00fcrich") + 1L);
                    StringWriter copy = new StringWriter();
                    try (Reader reader = stored.getCharacterStream()) {
                        reader.transferTo(copy);
                    }
                    assertThat(copy.toString()).isEqualTo(body);
                }
            }

            try (Statement statement = connection.createStatement();
                 ResultSet row = statement.executeQuery(
                         "SELECT body FROM document WHERE title = 'review-note.txt'")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("Reviewed by legal \u2713");
            }
        }
    }

    @Test
    void exportsLedgerRowByRowWithStreamingResultSet() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement export = connection.createStatement(
                     ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            export.setFetchSize(Integer.MIN_VALUE);
            long rows = 0;
            long cents = 0;
            long previous = 0;
            try (ResultSet ledger = export.executeQuery("SELECT seq, amount_cents FROM ledger ORDER BY seq")) {
                assertThat(ledger.getType()).isEqualTo(ResultSet.TYPE_FORWARD_ONLY);
                while (ledger.next()) {
                    long seq = ledger.getLong(1);
                    assertThat(seq).isEqualTo(previous + 1);
                    previous = seq;
                    cents += ledger.getLong(2);
                    rows++;
                    if (rows == 10) {
                        assertThatThrownBy(() -> {
                            try (Statement other = connection.createStatement()) {
                                other.executeQuery("SELECT COUNT(*) FROM ledger");
                            }
                        }).isInstanceOf(SQLException.class)
                                .hasMessageContaining("Streaming result set")
                                .hasMessageContaining("is still active");
                    }
                }
            }
            assertThat(rows).isEqualTo(LEDGER_ROWS);
            assertThat(cents).isEqualTo(expectedLedgerCents());

            try (Statement after = connection.createStatement();
                 ResultSet count = after.executeQuery("SELECT COUNT(*) FROM ledger")) {
                assertThat(count.next()).isTrue();
                assertThat(count.getLong(1)).isEqualTo(LEDGER_ROWS);
            }
        }
    }

    @Test
    void pagesLedgerThroughServerSideCursor() throws SQLException {
        try (Connection connection = cursorDataSource.getConnection();
             PreparedStatement page = connection.prepareStatement(
                     "SELECT seq, amount_cents FROM ledger WHERE seq > ? ORDER BY seq")) {
            page.setLong(1, 200);
            assertThat(page.getFetchSize()).isEqualTo(100);
            List<Long> checkpoints = new ArrayList<>();
            long rows = 0;
            try (ResultSet ledger = page.executeQuery()) {
                while (ledger.next()) {
                    rows++;
                    if (rows % 250 == 0) {
                        checkpoints.add(ledger.getLong("seq"));
                        try (Statement audit = connection.createStatement();
                             ResultSet count = audit.executeQuery("SELECT COUNT(*) FROM ledger")) {
                            assertThat(count.next()).isTrue();
                            assertThat(count.getLong(1)).isEqualTo(LEDGER_ROWS);
                        }
                    }
                }
            }
            assertThat(rows).isEqualTo(LEDGER_ROWS - 200);
            assertThat(checkpoints).containsExactly(450L, 700L, 950L, 1200L);
        }
    }

    private static long generatedKey(Statement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            assertThat(keys.next()).isTrue();
            return keys.getLong(1);
        }
    }

    private static byte[] scanBytes(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) (i * 31 + (i >>> 8));
        }
        return bytes;
    }

    private static String sha256(InputStream input) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8_192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static long expectedLedgerCents() {
        long total = 0;
        for (int seq = 1; seq <= LEDGER_ROWS; seq++) {
            total += seq * 7L % 1_000;
        }
        return total;
    }

    private static void createSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE document (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        title VARCHAR(120) NOT NULL,
                        scan LONGBLOB,
                        body LONGTEXT CHARACTER SET utf8mb4
                    ) ENGINE = InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE ledger (
                        seq INT PRIMARY KEY,
                        amount_cents INT NOT NULL
                    ) ENGINE = InnoDB
                    """);
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO ledger (seq, amount_cents) VALUES (?, ?)")) {
                for (int seq = 1; seq <= LEDGER_ROWS; seq++) {
                    insert.setInt(1, seq);
                    insert.setInt(2, (int) (seq * 7L % 1_000));
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
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
