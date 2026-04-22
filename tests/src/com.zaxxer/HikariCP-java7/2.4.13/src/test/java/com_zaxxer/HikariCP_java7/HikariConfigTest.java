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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HikariConfigTest {
    @Test
    public void setDriverClassNameInstantiatesConfiguredDriver() {
        HikariConfig config = new HikariConfig();

        config.setDriverClassName(TestDriver.class.getName());

        assertThat(config.getDriverClassName()).isEqualTo(TestDriver.class.getName());
    }

    @Test
    public void setDriverClassNameUsesThreadContextClassLoaderWhenPrimaryClassLoaderMisses() {
        HikariConfig config = new HikariConfig();
        String driverAlias = "com.example.ThreadContextDriver";
        TrackingClassLoader contextClassLoader = new TrackingClassLoader(
                HikariConfigTest.class.getClassLoader(),
                driverAlias,
                TestDriver.class
        );
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
            config.setDriverClassName(driverAlias);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

        assertThat(config.getDriverClassName()).isEqualTo(driverAlias);
        assertThat(contextClassLoader.getAttemptedLoads()).contains(driverAlias);
    }

    @Test
    public void setDriverClassNameFallsBackToThreadContextClassLoaderAfterPrimaryMiss() {
        HikariConfig config = new HikariConfig();
        TrackingClassLoader contextClassLoader = new TrackingClassLoader(HikariConfigTest.class.getClassLoader());
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        String missingDriverClassName = "com.example.MissingDriver";

        try {
            Thread.currentThread().setContextClassLoader(contextClassLoader);

            assertThatThrownBy(() -> config.setDriverClassName(missingDriverClassName))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to load class of driverClassName");
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

        assertThat(contextClassLoader.getAttemptedLoads()).contains(missingDriverClassName);
    }

    @Test
    public void copyStateCopiesConfigurationFields() {
        HikariConfig source = new HikariConfig();
        source.setCatalog("test-catalog");
        source.setJdbcUrl("jdbc:test:copy");
        source.setMaximumPoolSize(7);
        source.setMinimumIdle(2);
        source.setUsername("duke");
        source.setPassword("secret");
        source.addDataSourceProperty("cachePrepStmts", "true");

        HikariConfig target = new HikariConfig();
        source.copyState(target);

        assertThat(target.getCatalog()).isEqualTo("test-catalog");
        assertThat(target.getJdbcUrl()).isEqualTo("jdbc:test:copy");
        assertThat(target.getMaximumPoolSize()).isEqualTo(7);
        assertThat(target.getMinimumIdle()).isEqualTo(2);
        assertThat(target.getUsername()).isEqualTo("duke");
        assertThat(target.getPassword()).isEqualTo("secret");
        assertThat(target.getDataSourceProperties()).containsEntry("cachePrepStmts", "true");
    }

    @Test
    public void loadPropertiesReadsClasspathResource() {
        HikariConfig config = new HikariConfig("/hikari-config-test.properties");

        assertThat(config.getJdbcUrl()).isEqualTo("jdbc:test:from-resource");
        assertThat(config.getMaximumPoolSize()).isEqualTo(4);
        assertThat(config.getMinimumIdle()).isEqualTo(1);
        assertThat(config.getUsername()).isEqualTo("resource-user");
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
            return url != null && url.startsWith("jdbc:test");
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
        private final String resolvableClassName;
        private final Class<?> resolvedClass;

        private TrackingClassLoader(ClassLoader parent) {
            this(parent, null, null);
        }

        private TrackingClassLoader(ClassLoader parent, String resolvableClassName, Class<?> resolvedClass) {
            super(parent);
            this.attemptedLoads = new ArrayList<>();
            this.resolvableClassName = resolvableClassName;
            this.resolvedClass = resolvedClass;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            attemptedLoads.add(name);
            if (name.equals(resolvableClassName)) {
                return resolvedClass;
            }
            return super.loadClass(name);
        }

        private List<String> getAttemptedLoads() {
            return attemptedLoads;
        }
    }
}
