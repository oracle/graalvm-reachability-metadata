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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.SQLExceptionOverride;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HikariConfigTest {
    @Test
    public void settersInstantiateConfiguredClassesAfterContextLoaderMiss() {
        HikariConfig config = new HikariConfig();
        RejectingClassLoader contextClassLoader = new RejectingClassLoader(
                HikariConfigTest.class.getClassLoader(),
                Set.of(TestDriver.class.getName(), TestExceptionOverride.class.getName())
        );
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
            config.setDriverClassName(TestDriver.class.getName());
            config.setExceptionOverrideClassName(TestExceptionOverride.class.getName());
        }
        finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

        assertThat(config.getDriverClassName()).isEqualTo(TestDriver.class.getName());
        assertThat(config.getExceptionOverrideClassName()).isEqualTo(TestExceptionOverride.class.getName());
        assertThat(contextClassLoader.getRejectedLoads())
                .contains(TestDriver.class.getName(), TestExceptionOverride.class.getName());
    }

    @Test
    public void settersLoadConfiguredClassesFromContextClassLoader() {
        HikariConfig config = new HikariConfig();
        DelegatingClassLoader contextClassLoader = new DelegatingClassLoader(
                HikariConfigTest.class.getClassLoader(),
                Set.of(TestDriver.class.getName(), TestExceptionOverride.class.getName())
        );
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
            config.setDriverClassName(TestDriver.class.getName());
            config.setExceptionOverrideClassName(TestExceptionOverride.class.getName());
        }
        finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

        assertThat(config.getDriverClassName()).isEqualTo(TestDriver.class.getName());
        assertThat(config.getExceptionOverrideClassName()).isEqualTo(TestExceptionOverride.class.getName());
        assertThat(contextClassLoader.getDelegatedLoads())
                .contains(TestDriver.class.getName(), TestExceptionOverride.class.getName());
    }

    @Test
    public void copyStateToCopiesConfigurationFields() {
        HikariConfig source = new HikariConfig();
        source.setCatalog("test-catalog");
        source.setJdbcUrl("jdbc:test:copy");
        source.setMaximumPoolSize(7);
        source.setMinimumIdle(2);
        source.setUsername("duke");
        source.setPassword("secret");
        source.addDataSourceProperty("cachePrepStmts", "true");

        HikariConfig target = new HikariConfig();
        target.setCatalog("other-catalog");
        source.copyStateTo(target);

        assertThat(target.getCatalog()).isEqualTo("test-catalog");
        assertThat(target.getJdbcUrl()).isEqualTo("jdbc:test:copy");
        assertThat(target.getMaximumPoolSize()).isEqualTo(7);
        assertThat(target.getMinimumIdle()).isEqualTo(2);
        assertThat(target.getUsername()).isEqualTo("duke");
        assertThat(target.getPassword()).isEqualTo("secret");
        assertThat(target.getDataSourceProperties()).containsEntry("cachePrepStmts", "true");
    }

    @Test
    public void loadsPropertiesFromClasspathResource() {
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

    public static final class TestExceptionOverride implements SQLExceptionOverride {
        public TestExceptionOverride() {
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

    private static final class DelegatingClassLoader extends ClassLoader {
        private final Set<String> delegatedNames;
        private final List<String> delegatedLoads;

        private DelegatingClassLoader(ClassLoader parent, Set<String> delegatedNames) {
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
}
