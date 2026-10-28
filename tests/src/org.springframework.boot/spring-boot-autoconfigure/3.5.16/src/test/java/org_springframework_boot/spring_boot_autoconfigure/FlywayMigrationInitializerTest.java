/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FlywayMigrationInitializerTest {
    @Test
    void initializesTheDatabaseAndDoesNotRepeatAppliedMigrations() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:autoconfigure_migrations");
        dataSource.setUser("sa");
        try (Connection connection = dataSource.getConnection()) {
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/initializer")
                    .load();
            FlywayMigrationInitializer initializer = new FlywayMigrationInitializer(flyway);
            initializer.setOrder(12);
            assertEquals(12, initializer.getOrder());
            initializer.afterPropertiesSet();
            initializer.afterPropertiesSet();
            try (Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("SELECT label FROM initialization_record")) {
                assertTrue(result.next());
                assertEquals("migrated", result.getString(1));
                assertFalse(result.next());
            }
            assertEquals(1, flyway.info().applied().length);
            assertEquals(0, flyway.info().pending().length);
        }
    }
}
