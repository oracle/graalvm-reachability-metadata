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
    void registersCodahaleHealthChecksWhenRegistryIsConfigured() {
        String poolName = "health-check-pool";
        String connectivityCheckName = poolName + ".pool.ConnectivityCheck";
        HealthCheckRegistry healthCheckRegistry = new HealthCheckRegistry();
        HikariConfig config = new HikariConfig();
        config.setPoolName(poolName);
        config.setConnectionTimeout(1000);
        config.setMinimumIdle(0);
        config.setMaximumPoolSize(2);
        config.setDriverClassName(CustomDriver.class.getName());
        config.setJdbcUrl("jdbc:custom:health-check");
        config.setUsername("health-user");
        config.setPassword("health-password");
        config.setHealthCheckRegistry(healthCheckRegistry);

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            SortedMap<String, HealthCheck.Result> results = healthCheckRegistry.runHealthChecks();

            assertThat(dataSource.isRunning()).isTrue();
            assertThat(results).containsKey(connectivityCheckName);
            assertThat(results.get(connectivityCheckName).isHealthy()).isTrue();
        }
    }
}
