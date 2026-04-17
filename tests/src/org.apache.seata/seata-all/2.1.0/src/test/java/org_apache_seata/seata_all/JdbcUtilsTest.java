/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

import org.apache.seata.rm.datasource.util.JdbcUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbcUtilsTest {
    @Test
    void loadDriverUsesTheThreadContextClassLoaderBeforeFallingBack() throws SQLException {
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        RecordingClassLoader recordingClassLoader = new RecordingClassLoader(ContextLoadedDriver.class.getClassLoader());

        try {
            Thread.currentThread().setContextClassLoader(recordingClassLoader);

            Driver driver = JdbcUtils.loadDriver(ContextLoadedDriver.class.getName());

            assertThat(driver).isInstanceOf(ContextLoadedDriver.class);
            assertThat(recordingClassLoader.getLastRequestedClassName()).isEqualTo(ContextLoadedDriver.class.getName());
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    @Test
    void loadDriverFallsBackToClassForNameWhenNoContextClassLoaderIsAvailable() throws SQLException {
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(null);

            Driver driver = JdbcUtils.loadDriver(FallbackDriver.class.getName());

            assertThat(driver).isInstanceOf(FallbackDriver.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    public static class ContextLoadedDriver extends AbstractStubDriver {
        public ContextLoadedDriver() {
        }
    }

    public static class FallbackDriver extends AbstractStubDriver {
        public FallbackDriver() {
        }
    }

    private abstract static class AbstractStubDriver implements Driver {
        @Override
        public Connection connect(String url, Properties info) {
            return null;
        }

        @Override
        public boolean acceptsURL(String url) {
            return false;
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException("Not supported by test driver");
        }
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private String lastRequestedClassName;

        private RecordingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            lastRequestedClassName = name;
            return super.loadClass(name);
        }

        private String getLastRequestedClassName() {
            return lastRequestedClassName;
        }
    }
}
