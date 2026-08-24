/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.zaxxer.hikaricp.test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikaricp.test.driver.CustomDriver;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HikariCPTest {
    @Test
    void test() throws SQLException {
        HikariConfig config = new HikariConfig();

        config.setAutoCommit(false);
        config.setConnectionTimeout(1000);
        config.setMaximumPoolSize(10);
        config.setDriverClassName(CustomDriver.class.getName());
        config.setJdbcUrl("jdbc:custom:foo");
        config.setUsername("bart");
        config.setPassword("51mp50n");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        try (HikariDataSource ds = new HikariDataSource(config)) {
            for (int i = 0; i < 10; i++) {
                try (Connection connection = ds.getConnection()) {
                    assertNotNull(connection);
                }
            }
        }

    }

    @Test
    void micrometerMetricsAreReportedForH2() throws SQLException {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        HikariConfig config = new HikariConfig();
        config.setPoolName("H2Pool");
        config.setDriverClassName("org.h2.Driver");
        config.setJdbcUrl("jdbc:h2:mem:hikari;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setMetricRegistry(meterRegistry);

        try (HikariDataSource dataSource = new HikariDataSource(config);
             Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1");
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));

            Gauge activeGauge = meterRegistry.find("hikaricp.connections.active")
                    .tag("pool", "H2Pool")
                    .gauge();
            Gauge idleGauge = meterRegistry.find("hikaricp.connections.idle")
                    .tag("pool", "H2Pool")
                    .gauge();
            assertNotNull(activeGauge);
            assertNotNull(idleGauge);
            assertTrue(activeGauge.value() >= 0);
            assertTrue(idleGauge.value() >= 0);
        }
    }
}
