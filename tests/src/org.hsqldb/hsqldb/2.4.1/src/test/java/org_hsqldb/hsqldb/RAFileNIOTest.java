/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RAFileNIOTest {
    private static final int PAYLOAD_SIZE = 512 * 1024;
    private static final int ROW_COUNT = 20;
    private static final long NIO_SWITCH_THRESHOLD = 8L * 1024 * 1024;

    @TempDir
    Path tempDir;

    @Test
    public void shutsDownCachedTableAfterNioDataFileGrowth() throws Exception {
        Path databasePath = tempDir.resolve("nio-db");
        String url = databaseUrl(databasePath);
        byte[] payload = payload();

        try (Connection connection = openConnection(url); Statement statement = connection.createStatement()) {
            statement.execute("SET FILES NIO TRUE");
            statement.execute("CREATE CACHED TABLE entries (id INTEGER PRIMARY KEY, payload VARBINARY(524288))");

            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO entries VALUES (?, ?)")) {
                for (int row = 1; row <= ROW_COUNT; row++) {
                    insert.setInt(1, row);
                    insert.setBytes(2, payload);
                    assertEquals(1, insert.executeUpdate());
                }
            }

            assertCachedPayload(statement, payload);
            statement.execute("CHECKPOINT");
            assertTrue(Files.size(dataFile(databasePath)) > NIO_SWITCH_THRESHOLD);
            statement.execute("SHUTDOWN");
        }
    }

    private static void assertCachedPayload(Statement statement, byte[] expectedPayload) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("SELECT payload FROM entries WHERE id = " + ROW_COUNT)) {
            assertTrue(resultSet.next());
            assertArrayEquals(expectedPayload, resultSet.getBytes(1));
        }
    }

    private static byte[] payload() {
        byte[] payload = new byte[PAYLOAD_SIZE];

        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 251);
        }

        return payload;
    }

    private static Connection openConnection(String url) throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setUrl(url);
        dataSource.setUser("SA");
        dataSource.setPassword("");

        return dataSource.getConnection();
    }

    private static String databaseUrl(Path databasePath) {
        String normalizedPath = databasePath.toAbsolutePath().toString().replace(File.separatorChar, '/');

        return "jdbc:hsqldb:file:" + normalizedPath + ";hsqldb.nio_data_file=true";
    }

    private static Path dataFile(Path databasePath) {
        return databasePath.resolveSibling(databasePath.getFileName() + ".data");
    }
}
