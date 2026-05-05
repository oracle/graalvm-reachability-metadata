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
    public void dataSourceRegistersConfiguredDropwizardHealthChecks() {
        HealthCheckRegistry healthCheckRegistry = new HealthCheckRegistry();
        HikariConfig config = new HikariConfig();
        config.setPoolName("health-check-pool");
        config.setConnectionTimeout(1000);
        config.setInitializationFailTimeout(1000);
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);
        config.setDriverClassName(CustomDriver.class.getName());
        config.setJdbcUrl("jdbc:custom:health-checks");
        config.setHealthCheckRegistry(healthCheckRegistry);

        try (HikariDataSource ignored = new HikariDataSource(config)) {
            assertThat(healthCheckRegistry.getNames())
                    .contains("health-check-pool.pool.ConnectivityCheck");
        }
    }
}
