/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.HikariCP_java7;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import com.zaxxer.hikari.util.DriverDataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DriverDataSourceTest {
    @Test
    public void constructorInstantiatesConfiguredDriverClass() throws SQLException {
        DriverDataSource dataSource = new DriverDataSource(
                "jdbc:test-driver-data-source:valid",
                TestDriver.class.getName(),
                new Properties(),
                "duke",
                "secret"
        );

        assertThat(dataSource.getConnection()).isNull();
    }

    @Test
    public void constructorLoadsDriverFromThreadContextClassLoaderAfterPrimaryMiss() throws SQLException {
        String contextOnlyDriverClassName = "com.example.ContextOnlyDriver";
        TrackingClassLoader contextClassLoader = new TrackingClassLoader(
                DriverDataSourceTest.class.getClassLoader(),
                contextOnlyDriverClassName,
                TestDriver.class
        );
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(contextClassLoader);

            DriverDataSource dataSource = new DriverDataSource(
                    "jdbc:test-driver-data-source:context",
                    contextOnlyDriverClassName,
                    new Properties(),
                    "duke",
                    "secret"
            );

            assertThat(dataSource.getConnection()).isNull();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

        assertThat(contextClassLoader.getAttemptedLoads()).contains(contextOnlyDriverClassName);
    }

    public static final class TestDriver implements Driver {
        public TestDriver() {
        }

        @Override
        public Connection connect(String url, Properties info) {
            return null;
        }

        @Override
        public boolean acceptsURL(String url) {
            return url != null && url.startsWith("jdbc:test-driver-data-source:");
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
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }
    }

    private static final class TrackingClassLoader extends ClassLoader {
        private final List<String> attemptedLoads;
        private final String remappedClassName;
        private final Class<?> remappedClass;

        private TrackingClassLoader(ClassLoader parent, String remappedClassName, Class<?> remappedClass) {
            super(parent);
            this.attemptedLoads = new ArrayList<>();
            this.remappedClassName = remappedClassName;
            this.remappedClass = remappedClass;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            attemptedLoads.add(name);
            if (remappedClassName.equals(name)) {
                return remappedClass;
            }
            return super.loadClass(name);
        }

        private List<String> getAttemptedLoads() {
            return attemptedLoads;
        }
    }
}
