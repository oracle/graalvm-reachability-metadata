/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_microsoft_sqlserver.mssql_jdbc;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.microsoft.sqlserver.jdbc.SQLServerResultSet;
import com.microsoft.sqlserver.jdbc.SQLServerStatement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/// Drives large-object handling: streamed `VARBINARY(MAX)` and `NVARCHAR(MAX)`
/// parameters, the `Blob`, `Clob`, and `NClob` APIs, and adaptive response
/// buffering that streams column values off the wire as they are read.
///
/// The scenario is a document archive that uploads attachments and notes of
/// several megabytes to a real SQL Server, verifies them against server-side
/// hashes and lengths, and edits stored documents in place through LOB handles.
public class DocumentArchiveLobStreamingTest {

    private static final String IMAGE = "mcr.microsoft.com/mssql/server:2022-CU14-ubuntu-22.04";

    private static final String PASSWORD = "Archive#Lob2024";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(180);

    private static final String CONTAINER = "real-use-mssql-archive-" + UUID.randomUUID();

    private static final int ATTACHMENT_SIZE = 3 * 1024 * 1024 + 17;

    /// Cyrillic, CJK, and a surrogate-pair emoji, written as escapes to keep the source ASCII.
    private static final String MULTILINGUAL = "\u0410\u0440\u0445\u0438\u0432 \u6587\u66f8 \ud83d\udcc4 ";

    private static SQLServerDataSource archive;

    @BeforeAll
    static void startServer() throws Exception {
        docker("run", "-d", "--name", CONTAINER, "-e", "ACCEPT_EULA=Y", "-e", "MSSQL_SA_PASSWORD=" + PASSWORD,
                "-p", "127.0.0.1::1433", IMAGE);
        String mapping = docker("port", CONTAINER, "1433/tcp").lines().findFirst().orElseThrow();
        int port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));

        SQLServerDataSource admin = dataSource(port, "master");
        awaitServer(admin);
        try (Connection connection = admin.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE archive");
        }

        archive = dataSource(port, "archive");
        archive.setResponseBuffering("adaptive");
        try (Connection connection = archive.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE dbo.attachments (
                        id INT IDENTITY PRIMARY KEY,
                        file_name NVARCHAR(200) NOT NULL,
                        content_type VARCHAR(100) NOT NULL,
                        body VARBINARY(MAX) NOT NULL)
                    """);
            statement.execute("""
                    CREATE TABLE dbo.notes (
                        id INT IDENTITY PRIMARY KEY,
                        title NVARCHAR(200) NOT NULL,
                        summary VARCHAR(MAX) NULL,
                        body NVARCHAR(MAX) NOT NULL)
                    """);
        }
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @Test
    void attachmentOfUnknownLengthStreamsInAndOutIntact() throws Exception {
        MessageDigest uploaded = MessageDigest.getInstance("SHA-256");
        int id;
        try (Connection connection = archive.getConnection();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO dbo.attachments (file_name, content_type, body) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            insert.setNString(1, "scan-" + MULTILINGUAL.trim() + ".pdf");
            insert.setString(2, "application/pdf");
            insert.setBinaryStream(3, new PatternStream(ATTACHMENT_SIZE, uploaded));
            assertThat(insert.executeUpdate()).isEqualTo(1);
            try (ResultSet keys = insert.getGeneratedKeys()) {
                assertThat(keys.next()).isTrue();
                id = keys.getInt(1);
            }
        }
        byte[] expectedDigest = uploaded.digest();

        try (Connection connection = archive.getConnection();
                PreparedStatement check = connection.prepareStatement(
                        "SELECT DATALENGTH(body), HASHBYTES('SHA2_256', body) FROM dbo.attachments WHERE id = ?")) {
            check.setInt(1, id);
            try (ResultSet row = check.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getLong(1)).isEqualTo(ATTACHMENT_SIZE);
                assertThat(row.getBytes(2)).isEqualTo(expectedDigest);
            }
        }

        try (Connection connection = archive.getConnection();
                PreparedStatement download = connection.prepareStatement(
                        "SELECT file_name, content_type, body FROM dbo.attachments WHERE id = ?")) {
            assertThat(download.unwrap(SQLServerStatement.class).getResponseBuffering()).isEqualTo("adaptive");
            download.setInt(1, id);
            try (ResultSet row = download.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getNString("file_name")).isEqualTo("scan-" + MULTILINGUAL.trim() + ".pdf");
                assertThat(row.getString("content_type")).isEqualTo("application/pdf");
                MessageDigest downloaded = MessageDigest.getInstance("SHA-256");
                long total = 0;
                try (InputStream body = row.getBinaryStream("body")) {
                    byte[] buffer = new byte[64 * 1024];
                    for (int read = body.read(buffer); read != -1; read = body.read(buffer)) {
                        downloaded.update(buffer, 0, read);
                        total += read;
                    }
                }
                assertThat(total).isEqualTo(ATTACHMENT_SIZE);
                assertThat(downloaded.digest()).isEqualTo(expectedDigest);
                assertThat(row.next()).isFalse();
            }
        }
    }

    @Test
    void blobHandleSupportsRandomAccessPatchingAndTruncation() throws Exception {
        byte[] header = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
        byte[] marker = "%%EOF".getBytes(StandardCharsets.US_ASCII);
        byte[] original = new byte[20_000];
        PatternStream.fill(original);
        System.arraycopy(header, 0, original, 0, header.length);
        System.arraycopy(marker, 0, original, 15_000, marker.length);

        int id;
        try (Connection connection = archive.getConnection();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO dbo.attachments (file_name, content_type, body) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            Blob blob = connection.createBlob();
            assertThat(blob.setBytes(1, original)).isEqualTo(original.length);
            insert.setString(1, "contract.pdf");
            insert.setString(2, "application/pdf");
            insert.setBlob(3, blob);
            insert.executeUpdate();
            blob.free();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                assertThat(keys.next()).isTrue();
                id = keys.getInt(1);
            }
        }

        byte[] patch = "1.4".getBytes(StandardCharsets.US_ASCII);
        try (Connection connection = archive.getConnection()) {
            Blob stored;
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT body FROM dbo.attachments WHERE id = ?")) {
                select.setInt(1, id);
                try (ResultSet row = select.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    stored = row.getBlob(1);
                    assertThat(stored.length()).isEqualTo(original.length);
                    assertThat(stored.getBytes(1, header.length)).isEqualTo(header);
                    assertThat(stored.position(marker, 1)).isEqualTo(15_001L);
                    try (InputStream content = stored.getBinaryStream()) {
                        content.skipNBytes(15_000);
                        assertThat(content.readNBytes(marker.length)).isEqualTo(marker);
                    }

                    assertThat(stored.setBytes(6, patch)).isEqualTo(patch.length);
                    stored.truncate(15_000 + marker.length);
                }
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE dbo.attachments SET body = ? WHERE id = ?")) {
                update.setBlob(1, stored);
                update.setInt(2, id);
                assertThat(update.executeUpdate()).isEqualTo(1);
            }
            stored.free();
        }

        try (Connection connection = archive.getConnection();
                PreparedStatement check = connection.prepareStatement("""
                        SELECT DATALENGTH(body),
                               CAST(SUBSTRING(body, 1, 9) AS VARCHAR(9)),
                               CAST(SUBSTRING(body, DATALENGTH(body) - 4, 5) AS VARCHAR(5))
                        FROM dbo.attachments WHERE id = ?
                        """)) {
            check.setInt(1, id);
            try (ResultSet row = check.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getLong(1)).isEqualTo(15_000L + marker.length);
                assertThat(row.getString(2)).isEqualTo("%PDF-1.4\n");
                assertThat(row.getString(3)).isEqualTo("%%EOF");
            }
        }
    }

    @Test
    void multilingualNotesRoundTripThroughCharacterStreamsAndNClob() throws Exception {
        String longBody = MULTILINGUAL.repeat(20_000) + "end";
        String summary = "Quarterly archive audit; ".repeat(400);
        int streamedId;
        int clobId;
        try (Connection connection = archive.getConnection();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO dbo.notes (title, summary, body) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            insert.setNString(1, "streamed");
            insert.setCharacterStream(2, new StringReader(summary), summary.length());
            insert.setNCharacterStream(3, new StringReader(longBody));
            insert.executeUpdate();
            streamedId = generatedKey(insert);

            Clob summaryClob = connection.createClob();
            summaryClob.setString(1, "Signed by legal");
            NClob bodyClob = connection.createNClob();
            try (Writer writer = bodyClob.setCharacterStream(1)) {
                writer.write(MULTILINGUAL);
                writer.write("approved");
            }
            insert.setNString(1, "clob");
            insert.setClob(2, summaryClob);
            insert.setNClob(3, bodyClob);
            insert.executeUpdate();
            clobId = generatedKey(insert);
            summaryClob.free();
            bodyClob.free();
        }

        try (Connection connection = archive.getConnection();
                PreparedStatement check = connection.prepareStatement(
                        "SELECT DATALENGTH(summary), DATALENGTH(body) / 2 FROM dbo.notes WHERE id = ?")) {
            check.setInt(1, streamedId);
            try (ResultSet row = check.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getLong(1)).isEqualTo(summary.length());
                assertThat(row.getLong(2)).isEqualTo(longBody.length());
            }
        }

        try (Connection connection = archive.getConnection();
                PreparedStatement select = connection.prepareStatement(
                        "SELECT title, summary, body FROM dbo.notes WHERE id IN (?, ?) ORDER BY id")) {
            select.setInt(1, streamedId);
            select.setInt(2, clobId);
            try (ResultSet rows = select.executeQuery()) {
                SQLServerResultSet notes = rows.unwrap(SQLServerResultSet.class);

                assertThat(notes.next()).isTrue();
                assertThat(notes.getNString("title")).isEqualTo("streamed");
                assertThat(readAll(notes.getCharacterStream("summary"))).isEqualTo(summary);
                assertThat(readAll(notes.getNCharacterStream("body"))).isEqualTo(longBody);

                assertThat(notes.next()).isTrue();
                assertThat(notes.getNString("title")).isEqualTo("clob");
                Clob summaryClob = notes.getClob("summary");
                assertThat(summaryClob.getSubString(1, (int) summaryClob.length())).isEqualTo("Signed by legal");
                NClob bodyClob = notes.getNClob("body");
                assertThat(bodyClob.length()).isEqualTo(MULTILINGUAL.length() + "approved".length());
                assertThat(bodyClob.position("approved", 1)).isEqualTo(MULTILINGUAL.length() + 1L);
                assertThat(bodyClob.getSubString(1, MULTILINGUAL.length())).isEqualTo(MULTILINGUAL);
                summaryClob.free();
                bodyClob.free();

                assertThat(notes.next()).isFalse();
            }
        }
    }

    @Test
    void nullLargeObjectsReadBackAsNull() throws Exception {
        int id;
        try (Connection connection = archive.getConnection();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO dbo.notes (title, summary, body) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            insert.setNString(1, "draft");
            insert.setNull(2, Types.CLOB);
            insert.setNString(3, "");
            insert.executeUpdate();
            id = generatedKey(insert);
        }

        try (Connection connection = archive.getConnection();
                PreparedStatement select = connection.prepareStatement(
                        "SELECT summary, body FROM dbo.notes WHERE id = ?")) {
            select.setInt(1, id);
            try (ResultSet row = select.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getClob("summary")).isNull();
                assertThat(row.wasNull()).isTrue();
                NClob body = row.getNClob("body");
                assertThat(body.length()).isZero();
                body.free();
            }
        }
    }

    private static int generatedKey(PreparedStatement insert) throws SQLException {
        try (ResultSet keys = insert.getGeneratedKeys()) {
            assertThat(keys.next()).isTrue();
            return keys.getInt(1);
        }
    }

    private static String readAll(Reader reader) throws IOException {
        try (Reader source = reader) {
            StringBuilder text = new StringBuilder();
            char[] buffer = new char[8 * 1024];
            for (int read = source.read(buffer); read != -1; read = source.read(buffer)) {
                text.append(buffer, 0, read);
            }
            return text.toString();
        }
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

    /// A deterministic byte source of a fixed size that never holds the whole
    /// attachment in memory and digests every byte it hands to the driver.
    private static final class PatternStream extends InputStream {

        private final long size;

        private final MessageDigest digest;

        private long position;

        PatternStream(long size, MessageDigest digest) {
            this.size = size;
            this.digest = digest;
        }

        static void fill(byte[] target) {
            for (int i = 0; i < target.length; i++) {
                target[i] = valueAt(i);
            }
        }

        private static byte valueAt(long index) {
            return (byte) ((index * 31 + (index >>> 9)) & 0xFF);
        }

        @Override
        public int read() {
            if (position >= size) {
                return -1;
            }
            byte value = valueAt(position++);
            digest.update(value);
            return value & 0xFF;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (position >= size) {
                return -1;
            }
            int count = (int) Math.min(length, size - position);
            for (int i = 0; i < count; i++) {
                buffer[offset + i] = valueAt(position++);
            }
            digest.update(buffer, offset, count);
            return count;
        }
    }
}
