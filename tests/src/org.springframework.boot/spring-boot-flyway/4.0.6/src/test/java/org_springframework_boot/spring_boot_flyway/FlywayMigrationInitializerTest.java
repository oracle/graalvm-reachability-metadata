/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_flyway;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FlywayMigrationInitializerTest {

    @TempDir
    Path migrations;

    @Test
    @Timeout(120)
    void initializesDatabaseAndDoesNotRepeatAppliedMigrations() throws Exception {
        Files.writeString(migrations.resolve("V1__create_messages.sql"), """
                CREATE TABLE messages (id INTEGER PRIMARY KEY, body VARCHAR(100));
                INSERT INTO messages (id, body) VALUES (1, 'initialized');
                """);
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:initializer_" + UUID.randomUUID()
                + ";DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000");
        dataSource.setUser("sa");
        dataSource.setLoginTimeout(10);

        try (Connection connection = dataSource.getConnection()) {
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("filesystem:" + migrations.toAbsolutePath())
                    .load();
            FlywayMigrationInitializer initializer = new FlywayMigrationInitializer(flyway);

            initializer.afterPropertiesSet();

            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(10);
                try (ResultSet rows = statement.executeQuery("SELECT id, body FROM messages")) {
                    assertTrue(rows.next());
                    assertEquals(1, rows.getInt("id"));
                    assertEquals("initialized", rows.getString("body"));
                    assertFalse(rows.next());
                }
                statement.executeUpdate("UPDATE messages SET body = 'retained' WHERE id = 1");

                initializer.afterPropertiesSet();

                try (ResultSet rows = statement.executeQuery("SELECT body FROM messages")) {
                    assertTrue(rows.next());
                    assertEquals("retained", rows.getString("body"));
                    assertFalse(rows.next());
                }
            }
            assertEquals(1, flyway.info().applied().length);
            assertEquals(0, flyway.info().pending().length);
        }
    }
}
