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
import org.postgresql.PGConnection;
import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.largeobject.LargeObject;
import org.postgresql.largeobject.LargeObjectManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stores mail attachments in PostgreSQL large objects through the driver's Large Object API
 * (`LargeObjectManager` and `LargeObject`).
 *
 * An attachment store streams each upload into a new large object, records its OID with a
 * checksum in an `attachments` table, and later serves the content back as a stream, reads
 * byte ranges with `seek` for resumable downloads, rewrites and truncates a stored object in
 * place, and deletes an attachment by unlinking its large object, after which the OID can no
 * longer be opened.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AttachmentLargeObjectStoreTest {

    private static final String POSTGRES_IMAGE = "postgres:18-alpine";

    private static final String DATABASE = "mail";

    private static final String USER = "mailer";

    private static final String PASSWORD = "s3cret";

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
                    CREATE TABLE attachments (
                        id        serial PRIMARY KEY,
                        file_name text   NOT NULL,
                        content   oid    NOT NULL,
                        sha256    text   NOT NULL
                    )""");
        }
    }

    @AfterAll
    void stopDatabase() throws Exception {
        Process remove = new ProcessBuilder("docker", "rm", "-f", containerName).inheritIO().start();
        remove.waitFor(60, TimeUnit.SECONDS);
    }

    @Test
    void uploadedAttachmentIsStreamedBackAndServedInRanges() throws Exception {
        byte[] report = quarterlyReport();
        AttachmentStore store = new AttachmentStore();

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            int attachmentId = store.upload(connection, "q3-report.csv", report);
            connection.commit();

            StoredAttachment stored = store.describe(connection, attachmentId);
            assertThat(stored.fileName()).isEqualTo("q3-report.csv");
            assertThat(stored.size()).isEqualTo(report.length);
            assertThat(stored.sha256()).isEqualTo(sha256(report));

            byte[] downloaded = store.download(connection, attachmentId);
            assertThat(downloaded).isEqualTo(report);
            assertThat(sha256(downloaded)).isEqualTo(stored.sha256());

            int rangeStart = 70_000;
            byte[] range = store.downloadRange(connection, attachmentId, rangeStart, 64);
            byte[] expectedRange = new byte[64];
            System.arraycopy(report, rangeStart, expectedRange, 0, 64);
            assertThat(range).isEqualTo(expectedRange);

            byte[] tail = store.downloadRange(connection, attachmentId, report.length - 10, 64);
            assertThat(tail).hasSize(10);
            assertThat(new String(tail, StandardCharsets.US_ASCII)).isEqualTo(
                    new String(report, report.length - 10, 10, StandardCharsets.US_ASCII));
            connection.commit();
        }
    }

    @Test
    void editedAttachmentIsRewrittenInPlaceAndDeletedAttachmentIsGone() throws Exception {
        AttachmentStore store = new AttachmentStore();

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            byte[] draft = "Dear team,\nthe offsite is on MONDAY.\nRegards, Ada\n".getBytes(StandardCharsets.US_ASCII);
            int attachmentId = store.upload(connection, "invite.txt", draft);
            connection.commit();

            String corrected = store.replaceWord(connection, attachmentId, "MONDAY", "FRIDAY");
            connection.commit();
            assertThat(corrected).isEqualTo("Dear team,\nthe offsite is on FRIDAY.\nRegards, Ada\n");

            int signatureStart = corrected.indexOf("Regards");
            store.truncate(connection, attachmentId, signatureStart);
            connection.commit();

            byte[] trimmed = store.download(connection, attachmentId);
            assertThat(new String(trimmed, StandardCharsets.US_ASCII))
                    .isEqualTo("Dear team,\nthe offsite is on FRIDAY.\n");
            StoredAttachment described = store.describe(connection, attachmentId);
            assertThat(described.size()).isEqualTo(signatureStart);
            assertThat(described.sha256()).isEqualTo(sha256(trimmed));
            connection.commit();

            long oid = described.oid();
            store.delete(connection, attachmentId);
            connection.commit();

            LargeObjectManager manager = connection.unwrap(PGConnection.class).getLargeObjectAPI();
            assertThatThrownBy(() -> manager.open(oid, LargeObjectManager.READ))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining(Long.toString(oid));
            connection.rollback();

            try (PreparedStatement count = connection.prepareStatement(
                    "SELECT count(*) FROM pg_largeobject_metadata WHERE oid = ?")) {
                count.setLong(1, oid);
                try (ResultSet result = count.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isZero();
                }
            }
            connection.commit();
        }
    }

    /// Generates a CSV report larger than the driver's 8 KiB large-object buffer, so that
    /// uploads and downloads span many server round trips.
    private static byte[] quarterlyReport() {
        StringBuilder csv = new StringBuilder("order_id,region,amount\n");
        String[] regions = {"north", "south", "east", "west"};
        for (int order = 1; csv.length() < 100_000; order++) {
            csv.append(order).append(',').append(regions[order % regions.length]).append(',')
                    .append(order * 7 % 1000).append(".").append(order % 100).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    record StoredAttachment(String fileName, long oid, long size, String sha256) {
    }

    /// Application service that keeps attachment bodies in large objects and their
    /// bookkeeping in the `attachments` table; every call runs in the caller's transaction.
    static final class AttachmentStore {

        int upload(Connection connection, String fileName, byte[] content) throws Exception {
            LargeObjectManager manager = largeObjects(connection);
            long oid = manager.createLO(LargeObjectManager.READWRITE);
            LargeObject object = manager.open(oid, LargeObjectManager.WRITE);
            try (OutputStream body = object.getOutputStream()) {
                for (int offset = 0; offset < content.length; offset += 4096) {
                    body.write(content, offset, Math.min(4096, content.length - offset));
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO attachments (file_name, content, sha256) VALUES (?, ?, ?) RETURNING id")) {
                insert.setString(1, fileName);
                insert.setLong(2, oid);
                insert.setString(3, sha256(content));
                try (ResultSet generated = insert.executeQuery()) {
                    assertThat(generated.next()).isTrue();
                    return generated.getInt(1);
                }
            }
        }

        StoredAttachment describe(Connection connection, int attachmentId) throws SQLException {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT file_name, content, sha256 FROM attachments WHERE id = ?")) {
                select.setInt(1, attachmentId);
                try (ResultSet row = select.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    long oid = row.getLong("content");
                    LargeObject object = largeObjects(connection).open(oid, LargeObjectManager.READ);
                    try {
                        return new StoredAttachment(row.getString("file_name"), oid, object.size64(),
                                row.getString("sha256"));
                    } finally {
                        object.close();
                    }
                }
            }
        }

        byte[] download(Connection connection, int attachmentId) throws Exception {
            LargeObject object = open(connection, attachmentId, LargeObjectManager.READ);
            ByteArrayOutputStream copy = new ByteArrayOutputStream();
            try (InputStream body = object.getInputStream()) {
                body.transferTo(copy);
            }
            return copy.toByteArray();
        }

        byte[] downloadRange(Connection connection, int attachmentId, long start, int length) throws SQLException {
            LargeObject object = open(connection, attachmentId, LargeObjectManager.READ);
            try {
                object.seek64(start, LargeObject.SEEK_SET);
                assertThat(object.tell64()).isEqualTo(start);
                return object.read(length);
            } finally {
                object.close();
            }
        }

        String replaceWord(Connection connection, int attachmentId, String word, String replacement)
                throws SQLException {
            assertThat(replacement).hasSameSizeAs(word);
            LargeObject object = open(connection, attachmentId, LargeObjectManager.READWRITE);
            try {
                String text = new String(object.read(object.size()), StandardCharsets.US_ASCII);
                int position = text.indexOf(word);
                assertThat(position).isNotNegative();
                object.seek(position, LargeObject.SEEK_SET);
                object.write(replacement.getBytes(StandardCharsets.US_ASCII));
                object.seek(0);
                return new String(object.read(object.size()), StandardCharsets.US_ASCII);
            } finally {
                object.close();
            }
        }

        void truncate(Connection connection, int attachmentId, int length) throws Exception {
            LargeObject object = open(connection, attachmentId, LargeObjectManager.READWRITE);
            byte[] kept;
            try {
                object.truncate(length);
                kept = object.read(length);
            } finally {
                object.close();
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE attachments SET sha256 = ? WHERE id = ?")) {
                update.setString(1, sha256(kept));
                update.setInt(2, attachmentId);
                assertThat(update.executeUpdate()).isEqualTo(1);
            }
        }

        void delete(Connection connection, int attachmentId) throws SQLException {
            long oid;
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM attachments WHERE id = ? RETURNING content")) {
                delete.setInt(1, attachmentId);
                try (ResultSet removed = delete.executeQuery()) {
                    assertThat(removed.next()).isTrue();
                    oid = removed.getLong(1);
                }
            }
            largeObjects(connection).unlink(oid);
        }

        private LargeObject open(Connection connection, int attachmentId, int mode) throws SQLException {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT content FROM attachments WHERE id = ?")) {
                select.setInt(1, attachmentId);
                try (ResultSet row = select.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    return largeObjects(connection).open(row.getLong(1), mode);
                }
            }
        }

        private static LargeObjectManager largeObjects(Connection connection) throws SQLException {
            return connection.unwrap(PGConnection.class).getLargeObjectAPI();
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
