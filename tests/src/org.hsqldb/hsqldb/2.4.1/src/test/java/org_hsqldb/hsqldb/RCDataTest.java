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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.hsqldb.jdbc.JDBCDriver;
import org.hsqldb.lib.RCData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RCDataTest {
    @TempDir
    Path tempDir;

    @Test
    public void opensConnectionFromRcFileWithConfiguredDriver() throws Exception {
        Path rcFile = tempDir.resolve("database.rc");
        String databaseName = "rcdata" + System.nanoTime();

        Files.write(rcFile, rcFileContent(databaseName).getBytes(StandardCharsets.UTF_8));

        RCData rcData = new RCData(rcFile.toFile(), "coverage");

        try (Connection connection = rcData.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE rc_entries (id INTEGER PRIMARY KEY, label VARCHAR(32))");
            statement.executeUpdate("INSERT INTO rc_entries VALUES (1, 'configured-driver')");

            try (ResultSet resultSet = statement.executeQuery("SELECT label FROM rc_entries WHERE id = 1")) {
                assertTrue(resultSet.next());
                assertEquals("configured-driver", resultSet.getString(1));
                assertFalse(resultSet.next());
            }
        }
    }

    private static String rcFileContent(String databaseName) {
        return "urlid coverage\n"
                + "url jdbc:hsqldb:mem:" + databaseName + ";shutdown=true\n"
                + "username SA\n"
                + "password \n"
                + "driver " + JDBCDriver.class.getName() + "\n";
    }
}
