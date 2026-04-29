/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

import org.apache.seata.rm.datasource.util.JdbcUtils;
import org.junit.jupiter.api.Test;

public class JdbcUtilsTest {
    private static final String TEST_DRIVER_CLASS_NAME = TestJdbcDriver.class.getName();

    @Test
    void loadsDriverWithContextClassLoader() throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(JdbcUtilsTest.class.getClassLoader());
        try {
            Driver driver = JdbcUtils.loadDriver(TEST_DRIVER_CLASS_NAME);

            assertThat(driver).isInstanceOf(TestJdbcDriver.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void fallsBackToClassForNameWhenContextClassLoaderCannotLoadDriver() throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new DriverRejectingClassLoader(
                originalContextClassLoader, TEST_DRIVER_CLASS_NAME));
        try {
            Driver driver = JdbcUtils.loadDriver(TEST_DRIVER_CLASS_NAME);

            assertThat(driver).isInstanceOf(TestJdbcDriver.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    public static class TestJdbcDriver implements Driver {
        public TestJdbcDriver() {
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            return null;
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return url != null && url.startsWith("jdbc:seata-test:");
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
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
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }
    }

    private static class DriverRejectingClassLoader extends ClassLoader {
        private final String rejectedDriverClassName;

        DriverRejectingClassLoader(ClassLoader parent, String rejectedDriverClassName) {
            super(parent);
            this.rejectedDriverClassName = rejectedDriverClassName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (rejectedDriverClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }
    }
}
