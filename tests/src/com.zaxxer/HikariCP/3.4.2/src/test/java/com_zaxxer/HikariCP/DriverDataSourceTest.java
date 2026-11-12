/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.HikariCP;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import com.zaxxer.hikari.util.DriverDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DriverDataSourceTest {
    @Test
    public void instantiatesUnregisteredDriverUsingThreadContextLoader() throws SQLException {
        assertUnregisteredDriverConnects(true);
    }

    @Test
    public void instantiatesUnregisteredDriverUsingLibraryLoader() throws SQLException {
        assertUnregisteredDriverConnects(false);
    }

    private static void assertUnregisteredDriverConnects(boolean useContextLoader) throws SQLException {
        List<Driver> registeredDrivers = new ArrayList<>();
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (driver.getClass().getName().equals("org.h2.Driver")) {
                registeredDrivers.add(driver);
            }
        }

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            for (Driver driver : registeredDrivers) {
                DriverManager.deregisterDriver(driver);
            }
            Thread.currentThread().setContextClassLoader(useContextLoader ? DriverDataSourceTest.class.getClassLoader() : null);
            Properties properties = new Properties();
            properties.setProperty("INIT", "SET @application_value 73");
            DriverDataSource dataSource = new DriverDataSource(
                    "jdbc:h2:mem:direct_driver", "org.h2.Driver", properties, "sa", "");

            try (Connection connection = dataSource.getConnection()) {
                assertConfiguredConnection(connection);
            }
            try (Connection connection = dataSource.getConnection("sa", "")) {
                assertConfiguredConnection(connection);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(original);
            for (Driver driver : registeredDrivers) {
                DriverManager.registerDriver(driver);
            }
        }
    }

    private static void assertConfiguredConnection(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT CURRENT_USER, @application_value")) {
            assertTrue(rows.next());
            assertEquals("SA", rows.getString(1));
            assertEquals(73, rows.getInt(2));
        }
    }
}
