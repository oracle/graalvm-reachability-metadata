/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.zaxxer.hikaricp.test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.SQLExceptionOverride;
import com.zaxxer.hikaricp.test.driver.CustomDriver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

public class HikariConfigTest {
    @Test
    public void settersLoadConfiguredClassesFromHikariConfigClassLoaderWhenContextClassLoaderIsUnavailable() {
        HikariConfig config = new HikariConfig();
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(null);
            config.setDriverClassName(CustomDriver.class.getName());
            config.setExceptionOverrideClassName(TestExceptionOverride.class.getName());
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

        assertThat(config.getDriverClassName()).isEqualTo(CustomDriver.class.getName());
        assertThat(config.getExceptionOverrideClassName()).isEqualTo(TestExceptionOverride.class.getName());
        assertThat(config.getExceptionOverride()).isInstanceOf(TestExceptionOverride.class);
    }

    @Test
    public void constructorLoadsConfigurationFromClasspathResource() {
        HikariConfig config = new HikariConfig("/hikari-config-test.properties");

        assertThatConfigurationLoaded(config);
    }

    @Test
    public void constructorLoadsConfigurationFromClasspathResourceThroughClassLoaderFallback() {
        HikariConfig config = new HikariConfig("hikari-config-test.properties");

        assertThatConfigurationLoaded(config);
    }

    @Test
    public void settersCanBeInvokedThroughDynamicReflection() throws Exception {
        Properties props = new Properties();
        try (var is = getClass().getClassLoader().getResourceAsStream("hikari-config-test.properties")) {
            if (is == null) {
                throw new IllegalStateException("Cannot find property file: hikari-config-test.properties");
            }
            props.load(is);
        }

        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("dataSource.")) {
                props.setProperty(key.substring("dataSource.".length()), props.getProperty(key));
            }
        }

        String jdbcUrl = props.getProperty("jdbcUrl");
        String username = props.getProperty("username");
        String password = props.getProperty("password");
        String driverClassName = props.getProperty("driverClassName");
        String catalog = props.getProperty("catalog");

        int maximumPoolSize = Integer.parseInt(props.getProperty("maximumPoolSize"));
        int minimumIdle = Integer.parseInt(props.getProperty("minimumIdle"));

        long connectionTimeout = Long.parseLong(props.getProperty("connectionTimeout"));
        long idleTimeout = Long.parseLong(props.getProperty("idleTimeout"));
        long leakDetectionThreshold = Long.parseLong(props.getProperty("leakDetectionThreshold"));
        long maxLifetime = Long.parseLong(props.getProperty("maxLifetime"));
        long validationTimeout = Long.parseLong(props.getProperty("validationTimeout"));

        boolean readOnly = Boolean.parseBoolean(props.getProperty("readOnly"));
        boolean autoCommit = Boolean.parseBoolean(props.getProperty("autoCommit"));

        HikariConfig config = new HikariConfig();

        invokeDynamicSetter(config, "jdbcUrl", String.class, jdbcUrl);
        invokeDynamicSetter(config, "maximumPoolSize", int.class, maximumPoolSize);
        invokeDynamicSetter(config, "minimumIdle", int.class, minimumIdle);
        invokeDynamicSetter(config, "username", String.class, username);
        invokeDynamicSetter(config, "password", String.class, password);
        invokeDynamicSetter(config, "driverClassName", String.class, driverClassName);
        invokeDynamicSetter(config, "catalog", String.class, catalog);
        invokeDynamicSetter(config, "connectionTimeout", long.class, connectionTimeout);
        invokeDynamicSetter(config, "idleTimeout", long.class, idleTimeout);
        invokeDynamicSetter(config, "leakDetectionThreshold", long.class, leakDetectionThreshold);
        invokeDynamicSetter(config, "maxLifetime", long.class, maxLifetime);
        invokeDynamicSetter(config, "validationTimeout", long.class, validationTimeout);
        invokeDynamicSetter(config, "readOnly", boolean.class, readOnly);
        invokeDynamicSetter(config, "autoCommit", boolean.class, autoCommit);
        invokeDynamicSetter(config, "dataSourceProperties", Properties.class, props);

        assertThatConfigurationLoaded(config);
    }

    private void invokeDynamicSetter(Object target, String propertyName, Class<?> paramType, Object value) throws Exception {
        String setterName = "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        target.getClass().getMethod(setterName, paramType).invoke(target, value);
    }

    private static void assertThatConfigurationLoaded(HikariConfig config) {
        assertThat(config.getJdbcUrl()).isEqualTo("jdbc:test:from-resource");
        assertThat(config.getMaximumPoolSize()).isEqualTo(4);
        assertThat(config.getMinimumIdle()).isEqualTo(1);
        assertThat(config.getUsername()).isEqualTo("resource-user");
        assertThat(config.getPassword()).isEqualTo("resource-password");
        assertThat(config.getDriverClassName()).isEqualTo("org.h2.Driver");
        assertThat(config.getCatalog()).isEqualTo("resource-catalog");
        assertThat(config.getConnectionTimeout()).isEqualTo(500);
        assertThat(config.getIdleTimeout()).isEqualTo(100);
        assertThat(config.getLeakDetectionThreshold()).isEqualTo(11000);
        assertThat(config.getMaxLifetime()).isEqualTo(2800000);
        assertThat(config.getValidationTimeout()).isEqualTo(500);
        assertThat(config.isReadOnly()).isEqualTo(true);
        assertThat(config.isAutoCommit()).isEqualTo(true);
        assertThat(config.getDataSourceProperties())
            .containsEntry("cachePrepStmts", "true")
            .containsEntry("prepStmtCacheSize", "250");
    }

    public static final class TestExceptionOverride implements SQLExceptionOverride {
        public TestExceptionOverride() {
        }
    }
}
