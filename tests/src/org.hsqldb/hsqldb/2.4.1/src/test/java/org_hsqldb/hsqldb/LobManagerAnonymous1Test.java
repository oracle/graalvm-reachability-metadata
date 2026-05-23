/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LobManagerAnonymous1Test {

    @TempDir
    Path temporaryDirectory;

    @Test
    void openingNewMemoryDatabaseInitializesLobSchemaFromBundledResource() throws Exception {
        String databaseName = "lob_manager_schema_"
                + UUID.randomUUID().toString().replace('-', '_');
        byte[] payload = blobPayload();

        try (Connection connection = openConnection("jdbc:hsqldb:mem:" + databaseName);
                Statement statement = connection.createStatement()) {
            assertLobSchemaExists(statement);
            statement.execute("""
                    CREATE TABLE lob_schema_probe (
                        id INTEGER PRIMARY KEY,
                        payload BLOB
                    )
                    """);

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO lob_schema_probe VALUES (?, ?)")) {
                insert.setInt(1, 1);
                insert.setBytes(2, payload);

                assertThat(insert.executeUpdate()).isEqualTo(1);
            }

            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT payload FROM lob_schema_probe WHERE id = 1")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getBytes(1)).isEqualTo(payload);
                assertThat(resultSet.next()).isFalse();
            }

            statement.execute("SHUTDOWN");
        }
    }

    @Test
    void openingNewFileDatabaseInitializesLobSchemaFromBundledResource() throws Exception {
        String databasePath = temporaryDirectory.resolve("lob-manager-file-db").toString();
        byte[] payload = blobPayload();

        try (Connection connection = openConnection("jdbc:hsqldb:file:" + databasePath
                + ";hsqldb.lock_file=false");
                Statement statement = connection.createStatement()) {
            assertLobSchemaExists(statement);
            statement.execute("""
                    CREATE TABLE lob_file_probe (
                        id INTEGER PRIMARY KEY,
                        payload BLOB
                    )
                    """);

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO lob_file_probe VALUES (?, ?)")) {
                insert.setInt(1, 1);
                insert.setBytes(2, payload);

                assertThat(insert.executeUpdate()).isEqualTo(1);
            }

            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT payload FROM lob_file_probe WHERE id = 1")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getBytes(1)).isEqualTo(payload);
                assertThat(resultSet.next()).isFalse();
            }

            statement.execute("SHUTDOWN");
        }
    }

    private static void assertLobSchemaExists(Statement statement) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(
                "SELECT COUNT(*) FROM SYSTEM_LOBS.LOB_IDS")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isGreaterThanOrEqualTo(0);
            assertThat(resultSet.next()).isFalse();
        }
    }

    private static Connection openConnection(String url) throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();

        dataSource.setUrl(url);
        dataSource.setUser("SA");
        dataSource.setPassword("");

        return dataSource.getConnection();
    }

    private static byte[] blobPayload() {
        byte[] payload = new byte[4096];

        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) ('a' + i % 26);
        }

        return payload;
    }
}
