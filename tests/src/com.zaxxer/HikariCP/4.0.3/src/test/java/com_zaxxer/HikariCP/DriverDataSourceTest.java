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
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import com.zaxxer.hikari.util.DriverDataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DriverDataSourceTest {

    @Test
    void constructorInstantiatesDriverViaThreadContextClassLoader() {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader delegatingClassLoader = new ClassLoader(originalContextClassLoader) {
        };

        TrackingDriver.reset();

        try {
            Thread.currentThread().setContextClassLoader(delegatingClassLoader);

            DriverDataSource dataSource = new DriverDataSource(
                    "jdbc:driver-data-source-test:context-loader",
                    TrackingDriver.class.getName(),
                    new Properties(),
                    "alice",
                    "secret"
            );

            assertThat(dataSource).isNotNull();
            assertThat(TrackingDriver.instantiationCount()).isEqualTo(1);
        }
        finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void constructorFallsBackToDriverDataSourceClassLoaderWhenContextLoaderCannotLoadDriver() {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        RejectingClassLoader rejectingClassLoader = new RejectingClassLoader(
                originalContextClassLoader,
                TrackingDriver.class.getName()
        );

        TrackingDriver.reset();

        try {
            Thread.currentThread().setContextClassLoader(rejectingClassLoader);

            DriverDataSource dataSource = new DriverDataSource(
                    "jdbc:driver-data-source-test:fallback-loader",
                    TrackingDriver.class.getName(),
                    new Properties(),
                    null,
                    null
            );

            assertThat(dataSource).isNotNull();
            assertThat(TrackingDriver.instantiationCount()).isEqualTo(1);
        }
        finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    public static final class TrackingDriver implements Driver {
        private static final AtomicInteger INSTANTIATIONS = new AtomicInteger();

        public TrackingDriver() {
            INSTANTIATIONS.incrementAndGet();
        }

        static void reset() {
            INSTANTIATIONS.set(0);
        }

        static int instantiationCount() {
            return INSTANTIATIONS.get();
        }

        @Override
        public Connection connect(String url, Properties info) {
            return null;
        }

        @Override
        public boolean acceptsURL(String url) {
            return url != null && url.startsWith("jdbc:driver-data-source-test:");
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
            throw new SQLFeatureNotSupportedException();
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private final String rejectedClassName;

        private RejectingClassLoader(ClassLoader parent, String rejectedClassName) {
            super(parent);
            this.rejectedClassName = rejectedClassName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (rejectedClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }

            return super.loadClass(name);
        }
    }
}
