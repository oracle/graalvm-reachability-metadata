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
import java.sql.ResultSet;
import java.sql.Statement;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BlockObjectStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void reopensFileDatabaseUsingBlockBasedDataSpaceManager() throws Exception {
        String databasePath = temporaryDirectory.resolve("block_object_store_db").toString();
        String url = "jdbc:hsqldb:file:" + databasePath
                + ";hsqldb.lock_file=false";

        try (Connection connection = openConnection(url); Statement statement = connection.createStatement()) {
            statement.execute("SET FILES SPACE 1");
            statement.execute("""
                    CREATE CACHED TABLE block_store_items (
                        id INTEGER PRIMARY KEY,
                        label_text VARCHAR(64)
                    )
                    """);
            statement.executeUpdate("INSERT INTO block_store_items VALUES (1, 'alpha')");
            statement.executeUpdate("INSERT INTO block_store_items VALUES (2, 'beta')");
            statement.execute("CHECKPOINT");
            statement.execute("SHUTDOWN");
        }

        try (Connection connection = openConnection(url); Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM block_store_items")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(2);
            }

            statement.execute("SHUTDOWN");
        }
    }

    private static Connection openConnection(String url) throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();

        dataSource.setUrl(url);
        dataSource.setUser("SA");
        dataSource.setPassword("");

        return dataSource.getConnection();
    }
}
