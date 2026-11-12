/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.HikariCP;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.util.UtilityElf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UtilityElfTest {
    @Test
    public void createsAndConfiguresNamedDataSourceForPool() throws SQLException {
        try (HikariDataSource pool = new HikariDataSource()) {
            pool.setDataSourceClassName("org.h2.jdbcx.JdbcDataSource");
            pool.addDataSourceProperty("URL", "jdbc:h2:mem:utility_data_source");
            pool.addDataSourceProperty("user", "sa");
            pool.addDataSourceProperty("password", "");
            pool.setMaximumPoolSize(1);
            pool.setMinimumIdle(0);
            pool.setConnectionTimeout(30000);
            pool.setValidationTimeout(10000);
            pool.setInitializationFailTimeout(30000);

            try (Connection connection = pool.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT 17")) {
                assertTrue(rows.next());
                assertEquals(17, rows.getInt(1));
                assertTrue(UtilityElf.safeIsAssignableFrom(pool, "javax.sql.DataSource"));
                assertFalse(UtilityElf.safeIsAssignableFrom(pool, "java.sql.Connection"));
            }
        }
    }

    @Test
    public void constructsConfigurationUsingPropertiesConstructor() {
        Properties properties = new Properties();
        properties.setProperty("poolName", "argument-constructed-pool");
        properties.setProperty("maximumPoolSize", "6");
        properties.setProperty("jdbcUrl", "jdbc:h2:mem:argument_constructor");

        HikariConfig config = UtilityElf.createInstance("com.zaxxer.hikari.HikariConfig", HikariConfig.class, properties);
        config.validate();

        assertEquals("argument-constructed-pool", config.getPoolName());
        assertEquals(6, config.getMaximumPoolSize());
        assertEquals("jdbc:h2:mem:argument_constructor", config.getJdbcUrl());
    }
}
