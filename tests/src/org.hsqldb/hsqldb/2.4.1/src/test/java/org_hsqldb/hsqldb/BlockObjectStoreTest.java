/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BlockObjectStoreTest {
    @TempDir
    Path tempDir;

    @Test
    public void reopensCachedTableUsingBlockSpaceDirectory() throws Exception {
        String url = blockManagedDatabaseUrl(tempDir.resolve("block-space-db"));

        createCachedTable(url);
        assertCachedTableContent(url);
    }

    private static void createCachedTable(String url) throws Exception {
        try (Connection connection = openConnection(url); Statement statement = connection.createStatement()) {
            statement.execute("CREATE CACHED TABLE entries (id INTEGER PRIMARY KEY, name VARCHAR(32))");
            statement.executeUpdate("INSERT INTO entries VALUES (1, 'block-directory-value')");
            statement.execute("CHECKPOINT");
        }
    }

    private static void assertCachedTableContent(String url) throws Exception {
        try (Connection connection = openConnection(url);
                PreparedStatement query = connection.prepareStatement("SELECT name FROM entries WHERE id = ?")) {
            query.setInt(1, 1);

            try (ResultSet resultSet = query.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("block-directory-value", resultSet.getString(1));
                assertFalse(resultSet.next());
            }
        }
    }

    private static Connection openConnection(String url) throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setUrl(url);
        dataSource.setUser("SA");
        dataSource.setPassword("");

        return dataSource.getConnection();
    }

    private static String blockManagedDatabaseUrl(Path databasePath) {
        String normalizedPath = databasePath.toAbsolutePath().toString().replace(File.separatorChar, '/');

        return "jdbc:hsqldb:file:" + normalizedPath + ";hsqldb.files_space=1;shutdown=true";
    }
}
