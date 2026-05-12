/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.zaxxer.hikaricp.test;

import com.codahale.metrics.health.HealthCheck.Result;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikaricp.test.driver.CustomDriver;
import org.junit.jupiter.api.Test;

import java.util.SortedMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class HikariPoolTest {
    @Test
    void registersCodahaleHealthChecks() {
        HealthCheckRegistry healthCheckRegistry = new HealthCheckRegistry();
        HikariConfig config = new HikariConfig();

        config.setPoolName("health-check-pool");
        config.setConnectionTimeout(1000);
        config.setValidationTimeout(1000);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setDriverClassName(CustomDriver.class.getName());
        config.setJdbcUrl("jdbc:custom:health-check");
        config.setHealthCheckRegistry(healthCheckRegistry);
        config.addHealthCheckProperty("connectivityCheckTimeoutMs", "1000");

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            SortedMap<String, Result> healthChecks = healthCheckRegistry.runHealthChecks();

            assertTrue(healthChecks.containsKey("health-check-pool.pool.ConnectivityCheck"));
            assertTrue(healthChecks.get("health-check-pool.pool.ConnectivityCheck").isHealthy());
        }
    }
}
