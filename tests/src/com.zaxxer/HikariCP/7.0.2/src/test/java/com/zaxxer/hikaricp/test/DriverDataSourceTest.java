/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.zaxxer.hikaricp.test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import com.zaxxer.hikari.util.DriverDataSource;
import com.zaxxer.hikaricp.test.driver.CustomDriver;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DriverDataSourceTest {
    @Test
    void constructorLoadsDriverFromDriverDataSourceClassLoaderWhenContextClassLoaderIsUnavailable()
            throws SQLException {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(null);

            DriverDataSource dataSource = new DriverDataSource(
                    "jdbc:custom:driver-data-source",
                    CustomDriver.class.getName(),
                    new Properties(),
                    "duke",
                    "secret"
            );

            try (Connection connection = dataSource.getConnection()) {
                assertThat(connection).isNotNull();
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }
}
