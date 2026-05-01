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

        assertThat(config.getJdbcUrl()).isEqualTo("jdbc:test:from-resource");
        assertThat(config.getMaximumPoolSize()).isEqualTo(4);
        assertThat(config.getMinimumIdle()).isEqualTo(1);
        assertThat(config.getUsername()).isEqualTo("resource-user");
    }

    public static final class TestExceptionOverride implements SQLExceptionOverride {
        public TestExceptionOverride() {
        }
    }
}
