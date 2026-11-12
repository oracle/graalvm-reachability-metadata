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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HikariConfigTest {
    @Test
    public void loadsClasspathConfigurationAndCopiesItIntoRunningPool() throws SQLException {
        HikariConfig config = new HikariConfig("/hikari-pool.properties");
        assertEquals("resource-pool", config.getPoolName());
        assertEquals("jdbc:h2:mem:resource_pool", config.getJdbcUrl());
        assertEquals(2, config.getMaximumPoolSize());
        assertFalse(config.isAutoCommit());

        try (HikariDataSource pool = new HikariDataSource(config);
             Connection connection = pool.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT 42")) {
            config.setPoolName("independent-source");
            assertEquals("resource-pool", pool.getPoolName());
            assertEquals("sa", pool.getUsername());
            assertEquals(2, pool.getMaximumPoolSize());
            assertEquals(0, pool.getMinimumIdle());
            assertEquals(30000L, pool.getConnectionTimeout());
            assertEquals(10000L, pool.getValidationTimeout());
            assertFalse(connection.getAutoCommit());
            assertTrue(rows.next());
            assertEquals(42, rows.getInt(1));
            assertFalse(rows.next());
        }
    }

    @Test
    public void configuresJdbcDriverThroughThreadContextLoader() throws SQLException {
        assertDriverConnects(true);
    }

    @Test
    public void configuresJdbcDriverWithoutThreadContextLoader() throws SQLException {
        assertDriverConnects(false);
    }

    private static void assertDriverConnects(boolean useContextLoader) throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:configured_driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(30000);
        config.setValidationTimeout(10000);
        config.setInitializationFailTimeout(30000);
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(useContextLoader ? HikariConfigTest.class.getClassLoader() : null);
            config.setDriverClassName("org.h2.Driver");
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }

        try (HikariDataSource pool = new HikariDataSource(config);
             Connection connection = pool.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT CURRENT_USER")) {
            assertEquals("org.h2.Driver", pool.getDriverClassName());
            assertTrue(rows.next());
            assertEquals("SA", rows.getString(1));
        }
    }
}
