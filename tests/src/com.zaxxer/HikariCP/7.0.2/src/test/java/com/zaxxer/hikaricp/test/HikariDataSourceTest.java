/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.zaxxer.hikaricp.test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.IMetricsTracker;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import com.zaxxer.hikari.metrics.PoolStats;
import com.zaxxer.hikaricp.test.support.RecordingDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class HikariDataSourceTest {
    @Test
    void instantiatesConfiguresAndUsesDataSourceClassName() throws SQLException {
        RecordingDataSource.reset();
        RecordingMetricsTrackerFactory metricsTrackerFactory = new RecordingMetricsTrackerFactory();
        HikariConfig config = minimalPoolConfig("data-source-class-pool");
        config.setDataSourceClassName(RecordingDataSource.class.getName());
        config.setUsername("duke");
        config.setPassword("secret");
        config.setMetricsTrackerFactory(metricsTrackerFactory);
        config.addDataSourceProperty("serverName", "db.example.test");
        config.addDataSourceProperty("portNumber", "15432");
        config.addDataSourceProperty("useSsl", "true");

        try (HikariDataSource dataSource = new HikariDataSource(config);
                Connection connection = dataSource.getConnection()) {
            assertThat(connection.isValid(1)).isTrue();
            assertThat(connection.getAutoCommit()).isTrue();
            assertThat(dataSource.isRunning()).isTrue();
            assertThat(dataSource.getHikariConfigMXBean().getPoolName()).isEqualTo("data-source-class-pool");
            assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isGreaterThanOrEqualTo(0);
        }

        RecordingDataSource configuredDataSource = RecordingDataSource.lastInstance();
        assertThat(configuredDataSource).isNotNull();
        assertThat(configuredDataSource.getServerName()).isEqualTo("db.example.test");
        assertThat(configuredDataSource.getPortNumber()).isEqualTo(15432);
        assertThat(configuredDataSource.isUseSsl()).isTrue();
        assertThat(configuredDataSource.getLastUsername()).isEqualTo("duke");
        assertThat(configuredDataSource.getLastPassword()).isEqualTo("secret");
        assertThat(metricsTrackerFactory.createdTrackers()).isGreaterThanOrEqualTo(1);
    }

    private static HikariConfig minimalPoolConfig(String poolName) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(poolName);
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(Duration.ofMillis(1_000).toMillis());
        config.setValidationTimeout(Duration.ofMillis(500).toMillis());
        config.setInitializationFailTimeout(Duration.ofMillis(1_000).toMillis());
        return config;
    }

    public static final class RecordingMetricsTrackerFactory implements MetricsTrackerFactory {
        private final AtomicInteger createdTrackers = new AtomicInteger();

        @Override
        public IMetricsTracker create(String poolName, PoolStats poolStats) {
            createdTrackers.incrementAndGet();
            return new IMetricsTracker() {
            };
        }

        int createdTrackers() {
            return createdTrackers.get();
        }
    }
}
