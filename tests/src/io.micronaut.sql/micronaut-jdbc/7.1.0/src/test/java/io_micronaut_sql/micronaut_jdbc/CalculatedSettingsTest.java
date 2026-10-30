/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_sql.micronaut_jdbc;

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.jdbc.CalculatedSettings;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CalculatedSettingsTest {
    @Test
    void embeddedDefaultsCreateAUsableDatabase() throws Exception {
        TestJdbcConfiguration configuration = new TestJdbcConfiguration("settings_defaults");
        CalculatedSettings settings = configuration.getSettings();

        assertEquals("org.h2.Driver", settings.getDriverClassName());
        assertTrue(settings.getUrl().startsWith("jdbc:h2:mem:settings_defaults;"));
        assertEquals("sa", settings.getUsername());
        assertEquals("", settings.getPassword());
        assertEquals("SELECT 1", settings.getValidationQuery());

        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(settings.getUrl());
        dataSource.setUser(settings.getUsername());
        dataSource.setPassword(settings.getPassword());
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            statement.executeUpdate("CREATE TABLE measurements (id INT PRIMARY KEY, reading VARCHAR(30))");
            statement.executeUpdate("INSERT INTO measurements VALUES (1, 'ready')");
            try (ResultSet result = statement.executeQuery("SELECT reading FROM measurements WHERE id = 1")) {
                assertTrue(result.next());
                assertEquals("ready", result.getString(1));
            }
            try (ResultSet result = statement.executeQuery(settings.getValidationQuery())) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
            statement.execute("SHUTDOWN");
        }
    }

    @Test
    void explicitSettingsOverridePreviouslyCalculatedDefaults() {
        TestJdbcConfiguration configuration = new TestJdbcConfiguration("settings_overrides");
        CalculatedSettings settings = configuration.getSettings();
        assertEquals("sa", settings.getUsername());
        assertEquals("", settings.getPassword());
        assertEquals("SELECT 1", settings.getValidationQuery());
        assertEquals("org.h2.Driver", settings.getDriverClassName());
        assertTrue(settings.getUrl().startsWith("jdbc:h2:mem:settings_overrides;"));

        configuration.setUrl("jdbc:h2:mem:configured");
        configuration.setDriverClassName("org.h2.Driver");
        configuration.setUsername("application_user");
        configuration.setPassword("application_secret");
        configuration.setValidationQuery("SELECT 42");

        assertEquals("jdbc:h2:mem:configured", settings.getUrl());
        assertEquals("org.h2.Driver", settings.getDriverClassName());
        assertEquals("application_user", settings.getUsername());
        assertEquals("application_secret", settings.getPassword());
        assertEquals("SELECT 42", settings.getValidationQuery());
    }

    @Test
    void databaseUrlDeterminesDriverAndValidationQuery() {
        TestJdbcConfiguration configuration = new TestJdbcConfiguration("remote");
        configuration.setUrl("jdbc:oracle:thin:@localhost:1521:sample");
        CalculatedSettings settings = configuration.getSettings();

        assertEquals("oracle.jdbc.OracleDriver", settings.getDriverClassName());
        assertEquals(configuration.getConfiguredUrl(), settings.getUrl());
        assertEquals("SELECT 1 FROM DUAL", settings.getValidationQuery());
        assertNull(settings.getUsername());
        assertNull(settings.getPassword());
    }

    @Test
    void explicitlyConfiguredDriverMustBeAvailable() {
        TestJdbcConfiguration configuration = new TestJdbcConfiguration("missing_driver");
        configuration.setDriverClassName("example.unavailable.JdbcDriver");

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> configuration.getSettings().getDriverClassName());
        assertTrue(exception.getMessage().contains("missing_driver"));
        assertTrue(exception.getMessage().contains("example.unavailable.JdbcDriver"));
    }
}
