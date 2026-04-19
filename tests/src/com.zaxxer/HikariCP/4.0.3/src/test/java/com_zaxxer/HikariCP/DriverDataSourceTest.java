/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.HikariCP;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

import com.zaxxer.hikari.util.DriverDataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DriverDataSourceTest {
    @Test
    public void constructorLoadsDriverFromThreadContextClassLoader() throws SQLException {
        TrackingClassLoader contextClassLoader = new TrackingClassLoader(
                DriverDataSourceTest.class.getClassLoader(),
                Set.of(TestDriver.class.getName())
        );
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(contextClassLoader);

            DriverDataSource dataSource = new DriverDataSource(
                    "jdbc:test-driver-data-source:context",
                    TestDriver.class.getName(),
                    new Properties(),
                    "duke",
                    "secret"
            );

            assertThat(dataSource.getConnection()).isNull();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

        assertThat(contextClassLoader.getDelegatedLoads()).contains(TestDriver.class.getName());
    }

    @Test
    public void constructorFallsBackToDriverDataSourceClassLoaderAfterContextLoaderMiss() throws SQLException {
        RejectingClassLoader contextClassLoader = new RejectingClassLoader(
                DriverDataSourceTest.class.getClassLoader(),
                Set.of(TestDriver.class.getName())
        );
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(contextClassLoader);

            DriverDataSource dataSource = new DriverDataSource(
                    "jdbc:test-driver-data-source:fallback",
                    TestDriver.class.getName(),
                    new Properties(),
                    "duke",
                    "secret"
            );

            assertThat(dataSource.getConnection()).isNull();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

        assertThat(contextClassLoader.getRejectedLoads()).contains(TestDriver.class.getName());
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
        private final Set<String> delegatedNames;
        private final List<String> delegatedLoads;

        private TrackingClassLoader(ClassLoader parent, Set<String> delegatedNames) {
            super(parent);
            this.delegatedNames = delegatedNames;
            this.delegatedLoads = new ArrayList<>();
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (delegatedNames.contains(name)) {
                delegatedLoads.add(name);
            }
            return super.loadClass(name);
        }

        private List<String> getDelegatedLoads() {
            return delegatedLoads;
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private final Set<String> rejectedNames;
        private final List<String> rejectedLoads;

        private RejectingClassLoader(ClassLoader parent, Set<String> rejectedNames) {
            super(parent);
            this.rejectedNames = rejectedNames;
            this.rejectedLoads = new ArrayList<>();
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (rejectedNames.contains(name)) {
                rejectedLoads.add(name);
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }

        private List<String> getRejectedLoads() {
            return rejectedLoads;
        }
    }
}
