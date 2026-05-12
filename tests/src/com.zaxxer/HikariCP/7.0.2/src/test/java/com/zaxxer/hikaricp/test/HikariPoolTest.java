/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.zaxxer.hikaricp.test;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikaricp.test.driver.CustomDriver;
import org.junit.jupiter.api.Test;

import java.util.SortedMap;

import static org.assertj.core.api.Assertions.assertThat;

public class HikariPoolTest {
    @Test
    public void constructorRegistersCodahaleHealthChecks() {
        String poolName = "health-check-pool";
        HealthCheckRegistry healthCheckRegistry = new HealthCheckRegistry();
        HikariConfig config = new HikariConfig();
        config.setPoolName(poolName);
        config.setDriverClassName(CustomDriver.class.getName());
        config.setJdbcUrl("jdbc:custom:health-check");
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(1_000L);
        config.setInitializationFailTimeout(-1L);
        config.setHealthCheckRegistry(healthCheckRegistry);

        try (HikariDataSource ignored = new HikariDataSource(config)) {
            SortedMap<String, HealthCheck.Result> results = healthCheckRegistry.runHealthChecks();

            assertThat(results).containsOnlyKeys(poolName + ".pool.ConnectivityCheck");
            assertThat(results.get(poolName + ".pool.ConnectivityCheck").isHealthy()).isTrue();
        }
    }
}
