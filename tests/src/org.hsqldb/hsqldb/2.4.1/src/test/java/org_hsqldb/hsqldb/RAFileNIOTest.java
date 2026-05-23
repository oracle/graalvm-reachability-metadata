/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;

import org.hsqldb.Database;
import org.hsqldb.Session;
import org.hsqldb.jdbc.JDBCConnection;
import org.hsqldb.jdbc.JDBCDataSource;
import org.hsqldb.persist.RAFileHybrid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RAFileNIOTest {
    private static final int PAYLOAD_SIZE = 1024 * 1024;
    private static final int ROW_COUNT = 10;
    private static final long NIO_THRESHOLD = 8L * 1024L * 1024L;

    @TempDir
    Path temporaryDirectory;

    @Test
    void cachedTableShutdownClosesNioMappedDataFile() throws Exception {
        String databasePath = temporaryDirectory.resolve("cached-table-db").toString();
        String url = "jdbc:hsqldb:file:" + databasePath
                + ";hsqldb.lock_file=false";

        try (Connection connection = openConnection(url); Statement statement = connection.createStatement()) {
            statement.execute("SET FILES NIO TRUE");
            statement.execute("SET FILES NIO SIZE 16");
            statement.execute("CREATE CACHED TABLE CACHED_DATA (ID INTEGER PRIMARY KEY, PAYLOAD VARBINARY(1048576))");

            byte[] payload = new byte[PAYLOAD_SIZE];

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO CACHED_DATA (ID, PAYLOAD) VALUES (?, ?)")) {
                for (int i = 0; i < ROW_COUNT; i++) {
                    Arrays.fill(payload, (byte) i);
                    insert.setInt(1, i);
                    insert.setBytes(2, payload);
                    insert.executeUpdate();
                }
            }

            try (ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM CACHED_DATA")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(ROW_COUNT);
            }

            statement.execute("SHUTDOWN");
        }

        Path dataFile = temporaryDirectory.resolve("cached-table-db.data");

        assertThat(dataFile).exists();
        assertThat(Files.size(dataFile)).isGreaterThan(NIO_THRESHOLD);
    }

    @Test
    void closeUnmapsReadOnlyMappedBufferOpenedByHybridRandomAccessFile() throws Exception {
        String databaseName = "rafilenio_readonly_" + Long.toUnsignedString(System.nanoTime());
        Path randomAccessFile = temporaryDirectory.resolve("readonly-cached-table.data");
        byte[] expected = new byte[] { 2, 7, 1, 8 };
        long markerPosition = NIO_THRESHOLD + 1;

        try (RandomAccessFile seedFile = new RandomAccessFile(randomAccessFile.toFile(), "rw")) {
            seedFile.setLength(markerPosition + expected.length);
            seedFile.seek(markerPosition);
            seedFile.write(expected);
        }

        try (Connection connection = openConnection("jdbc:hsqldb:mem:" + databaseName + ";shutdown=true");
                Statement statement = connection.createStatement()) {
            statement.execute("SET FILES NIO TRUE");
            statement.execute("SET FILES NIO SIZE 16");

            Database database = databaseSession(connection).getDatabase();
            RAFileHybrid file = new RAFileHybrid(database, randomAccessFile.toString(), true);

            try {
                assertThat(file.length()).isEqualTo(markerPosition + expected.length);
                file.seek(markerPosition);

                byte[] actual = new byte[expected.length];

                file.read(actual, 0, actual.length);

                assertThat(actual).isEqualTo(expected);
            } finally {
                file.close();
            }
        }
    }

    @Test
    void closeUnmapsMappedBufferUsedByHybridRandomAccessFile() throws Exception {
        String databaseName = "rafilenio_" + Long.toUnsignedString(System.nanoTime());
        String randomAccessFileName = temporaryDirectory.resolve("cached-table.data").toString();

        try (Connection connection = openConnection("jdbc:hsqldb:mem:" + databaseName + ";shutdown=true");
                Statement statement = connection.createStatement()) {
            statement.execute("SET FILES NIO TRUE");
            statement.execute("SET FILES NIO SIZE 16");

            Database database = databaseSession(connection).getDatabase();
            byte[] expected = new byte[] { 3, 1, 4, 1, 5, 9 };

            RAFileHybrid file = new RAFileHybrid(database, randomAccessFileName, false);

            try {
                assertThat(file.ensureLength(NIO_THRESHOLD + expected.length)).isTrue();
                file.seek(NIO_THRESHOLD);
                file.write(expected, 0, expected.length);
                file.seek(NIO_THRESHOLD);

                byte[] actual = new byte[expected.length];

                file.read(actual, 0, actual.length);

                assertThat(actual).isEqualTo(expected);
                assertThat(file.length()).isEqualTo(2 * NIO_THRESHOLD);
            } finally {
                file.close();
            }

            assertThat(temporaryDirectory.resolve("cached-table.data")).exists();
        }
    }

    private static Connection openConnection(String url) throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();

        dataSource.setUrl(url);
        dataSource.setUser("SA");
        dataSource.setPassword("");

        return dataSource.getConnection();
    }

    private static Session databaseSession(Connection connection) throws Exception {
        JDBCConnection hsqldbConnection = connection.unwrap(JDBCConnection.class);

        return (Session) hsqldbConnection.getSession();
    }
}
