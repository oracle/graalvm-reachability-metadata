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
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.SQLExceptionOverride;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HikariConfigTest {

    @Test
    void setDriverAndExceptionOverrideClassNameInstantiateClassesViaFallbackClassLoader() {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        RejectingClassLoader rejectingClassLoader = new RejectingClassLoader(
                originalContextClassLoader,
                TestDriver.class.getName(),
                TestExceptionOverride.class.getName()
        );

        TestDriver.reset();
        TestExceptionOverride.reset();

        try {
            Thread.currentThread().setContextClassLoader(rejectingClassLoader);

            HikariConfig config = new HikariConfig();
            config.setDriverClassName(TestDriver.class.getName());
            config.setExceptionOverrideClassName(TestExceptionOverride.class.getName());

            assertThat(config.getDriverClassName()).isEqualTo(TestDriver.class.getName());
            assertThat(config.getExceptionOverrideClassName()).isEqualTo(TestExceptionOverride.class.getName());
            assertThat(TestDriver.instantiationCount()).isEqualTo(1);
            assertThat(TestExceptionOverride.instantiationCount()).isEqualTo(1);
        }
        finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void setDriverClassNameLoadsDriverFromThreadContextClassLoader() {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader delegatingClassLoader = new ClassLoader(originalContextClassLoader) {
        };

        TestDriver.reset();

        try {
            Thread.currentThread().setContextClassLoader(delegatingClassLoader);

            HikariConfig config = new HikariConfig();
            config.setDriverClassName(TestDriver.class.getName());

            assertThat(config.getDriverClassName()).isEqualTo(TestDriver.class.getName());
            assertThat(TestDriver.instantiationCount()).isEqualTo(1);
        }
        finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void copyStateToCopiesConfigFields() {
        HikariConfig source = new HikariConfig();
        source.setPoolName("copied-pool");
        source.setJdbcUrl("jdbc:copied:test");
        source.setUsername("alice");
        source.setPassword("secret");
        source.setMaximumPoolSize(7);
        source.addDataSourceProperty("cachePrepStmts", "true");

        HikariConfig target = new HikariConfig();
        source.copyStateTo(target);

        assertThat(target.getPoolName()).isEqualTo("copied-pool");
        assertThat(target.getJdbcUrl()).isEqualTo("jdbc:copied:test");
        assertThat(target.getUsername()).isEqualTo("alice");
        assertThat(target.getPassword()).isEqualTo("secret");
        assertThat(target.getMaximumPoolSize()).isEqualTo(7);
        assertThat(target.getDataSourceProperties()).containsEntry("cachePrepStmts", "true");
    }

    @Test
    void stringConstructorLoadsPropertiesFromClasspathResource() {
        HikariConfig config = new HikariConfig("/hikaricp/hikari-config-test.properties");

        assertThat(config.getPoolName()).isEqualTo("resource-configured-pool");
        assertThat(config.getJdbcUrl()).isEqualTo("jdbc:resource:test");
        assertThat(config.getMaximumPoolSize()).isEqualTo(9);
        assertThat(config.getMinimumIdle()).isEqualTo(3);
    }

    public static final class TestDriver implements Driver {
        private static final AtomicInteger INSTANTIATIONS = new AtomicInteger();

        public TestDriver() {
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
            return url != null && url.startsWith("jdbc:hikari-config-test:");
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

    public static final class TestExceptionOverride implements SQLExceptionOverride {
        private static final AtomicInteger INSTANTIATIONS = new AtomicInteger();

        public TestExceptionOverride() {
            INSTANTIATIONS.incrementAndGet();
        }

        static void reset() {
            INSTANTIATIONS.set(0);
        }

        static int instantiationCount() {
            return INSTANTIATIONS.get();
        }

        @java.lang.Override
        public SQLExceptionOverride.Override adjudicate(SQLException sqlException) {
            return SQLExceptionOverride.Override.DO_NOT_EVICT;
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private final String[] rejectedClassNames;

        private RejectingClassLoader(ClassLoader parent, String... rejectedClassNames) {
            super(parent);
            this.rejectedClassNames = rejectedClassNames;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            for (String rejectedClassName : rejectedClassNames) {
                if (rejectedClassName.equals(name)) {
                    throw new ClassNotFoundException(name);
                }
            }

            return super.loadClass(name);
        }
    }
}
