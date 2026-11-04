/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2;

import org.apache.commons.configuration2.DatabaseConfiguration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// A database-backed configuration lets an order service read and update settings stored in its application database.
public class DatabaseBackedConfigurationTest {
    @Test
    @Timeout(60)
    public void readsAndUpdatesApplicationSettingsThroughJdbc() throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:configuration;DB_CLOSE_DELAY=-1");

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE APP_CONFIG (
                        CONFIG_NAME VARCHAR(64) NOT NULL,
                        PROPERTY_KEY VARCHAR(128) NOT NULL,
                        PROPERTY_VALUE VARCHAR(512) NOT NULL
                    )
                    """);
        }

        DatabaseConfiguration configuration = new DatabaseConfiguration();
        configuration.setDataSource(dataSource);
        configuration.setTable("APP_CONFIG");
        configuration.setConfigurationNameColumn("CONFIG_NAME");
        configuration.setKeyColumn("PROPERTY_KEY");
        configuration.setValueColumn("PROPERTY_VALUE");
        configuration.setConfigurationName("orders");
        configuration.setAutoCommit(true);

        configuration.addProperty("service.timeout.seconds", 30);
        configuration.addProperty("features", "audit");
        configuration.addProperty("features", "metrics");

        assertEquals(30, configuration.getInt("service.timeout.seconds"));
        assertEquals(List.of("audit", "metrics"), configuration.getList("features"));
        assertTrue(configuration.containsKey("service.timeout.seconds"));

        configuration.clearProperty("service.timeout.seconds");

        assertFalse(configuration.containsKey("service.timeout.seconds"));
        assertEquals(List.of("audit", "metrics"), configuration.getList("features"));
    }
}
