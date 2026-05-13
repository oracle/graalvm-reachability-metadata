/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.zaxxer.hikaricp.test;

import com.codahale.metrics.health.HealthCheckRegistry;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikaricp.test.driver.CustomDriver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HikariPoolTest {
    @Test
    public void registersCodahaleHealthChecksFromConfiguredRegistry() {
        HealthCheckRegistry healthCheckRegistry = new HealthCheckRegistry();
        HikariConfig config = new HikariConfig();
        config.setPoolName("healthPool");
        config.setConnectionTimeout(1000);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setDriverClassName(CustomDriver.class.getName());
        config.setJdbcUrl("jdbc:custom:health-check");
        config.setHealthCheckRegistry(healthCheckRegistry);

        try (HikariDataSource ignored = new HikariDataSource(config)) {
            assertThat(healthCheckRegistry.getNames()).contains("healthPool.pool.ConnectivityCheck");
        }
    }
}
