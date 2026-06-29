/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.hsqldb.jdbc.JDBCDataSource;
import org.hsqldb.lib.java.JavaSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JavaSystemTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void cachedTableShutdownUnmapsNioDataFile() throws Exception {
        Path databasePath = temporaryDirectory.resolve("cached-table-database").toAbsolutePath();
        JDBCDataSource dataSource = new JDBCDataSource();

        dataSource.setUrl(
                "jdbc:hsqldb:file:" + databasePath + ";hsqldb.nio_data_file=true;shutdown=true");
        dataSource.setUser("SA");
        dataSource.setPassword("");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE CACHED TABLE mapped_items(
                        id INTEGER PRIMARY KEY,
                        name VARCHAR(20))
                    """);
            statement.execute("INSERT INTO mapped_items VALUES (1, 'alpha')");

            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT name FROM mapped_items WHERE id = 1")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(1)).isEqualTo("alpha");
            }

            statement.execute("SHUTDOWN");
        }
    }

    @Test
    void unmapMappedByteBufferReleasesFileMapping() throws Exception {
        String originalJavaVersion = System.getProperty("java.version");

        System.setProperty("java.version", "11");
        try {
            assertThat(JavaSystem.javaVersion()).isGreaterThan(8);

            Path mappedFile = temporaryDirectory.resolve("mapped-buffer.dat");

            try (FileChannel channel = FileChannel.open(
                    mappedFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                channel.truncate(4096);

                MappedByteBuffer mappedBuffer = channel.map(
                        FileChannel.MapMode.READ_WRITE,
                        0,
                        4096);
                mappedBuffer.put(0, (byte) 42);
                mappedBuffer.force();

                Throwable unmapFailure = JavaSystem.unmap(mappedBuffer);

                assertThat(unmapFailure).isNull();
            }
        } finally {
            if (originalJavaVersion == null) {
                System.clearProperty("java.version");
            } else {
                System.setProperty("java.version", originalJavaVersion);
            }
        }
    }
}
