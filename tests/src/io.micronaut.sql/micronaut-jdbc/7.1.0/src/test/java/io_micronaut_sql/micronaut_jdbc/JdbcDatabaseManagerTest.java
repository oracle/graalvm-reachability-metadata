/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_sql.micronaut_jdbc;

import io.micronaut.jdbc.JdbcDatabaseManager;
import io.micronaut.jdbc.JdbcDatabaseManager.EmbeddedJdbcDatabase;
import io.micronaut.jdbc.JdbcDatabaseManager.JdbcDatabase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JdbcDatabaseManagerTest {
    @Test
    void discoversEmbeddedDatabaseAndProvidesConnectionDefaults() {
        EmbeddedJdbcDatabase database = JdbcDatabaseManager.get(getClass().getClassLoader()).orElseThrow();

        assertEquals("org.h2.Driver", database.getDriverClassName());
        assertTrue(database.containsPrefix("h2"));
        assertEquals("SELECT 1", database.getValidationQuery());
        assertEquals("sa", database.getDefaultUsername());
        assertEquals("", database.getDefaultPassword());
        assertEquals("jdbc:h2:mem:orders;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE", database.getUrl("orders"));
        assertEquals("jdbc:h2:mem:devDb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE", database.getUrl(null));
        assertTrue(JdbcDatabaseManager.isEmbedded(database.getDriverClassName()));
        assertEquals(database, JdbcDatabaseManager.findEmbeddedDatabase(database.getDriverClassName()).orElseThrow());
    }

    @Test
    void matchesJdbcUrlPrefixesWithoutConnectingToExternalDatabases() {
        JdbcDatabase postgres = JdbcDatabaseManager.findDatabase("jdbc:POSTGRESQL://localhost/orders").orElseThrow();
        assertEquals("org.postgresql.Driver", postgres.getDriverClassName());
        assertEquals("SELECT 1", postgres.getValidationQuery());
        assertTrue(postgres.containsPrefix("postgresql"));
        assertFalse(postgres.containsPrefix("mysql"));
        assertFalse(JdbcDatabaseManager.isEmbedded(postgres.getDriverClassName()));
        assertTrue(JdbcDatabaseManager.findEmbeddedDatabase(postgres.getDriverClassName()).isEmpty());

        JdbcDatabase oracle = JdbcDatabaseManager.findDatabase("jdbc:oracle:thin:@localhost:1521:orders").orElseThrow();
        assertEquals("oracle.jdbc.OracleDriver", oracle.getDriverClassName());
        assertEquals("SELECT 1 FROM DUAL", oracle.getValidationQuery());
    }

    @Test
    void unknownOrEmptyJdbcUrlHasNoDatabaseDefinition() {
        assertTrue(JdbcDatabaseManager.findDatabase("jdbc:unregistered:orders").isEmpty());
        assertTrue(JdbcDatabaseManager.findDatabase("").isEmpty());
    }

    @Test
    void rejectsNonJdbcUrls() {
        assertThrows(IllegalArgumentException.class,
                () -> JdbcDatabaseManager.findDatabase("https://localhost/orders"));
    }
}
