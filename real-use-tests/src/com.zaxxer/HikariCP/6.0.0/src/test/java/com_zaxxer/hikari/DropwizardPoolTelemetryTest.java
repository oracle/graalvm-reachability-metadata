/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.hikari;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises HikariCP's Dropwizard Metrics integration for application pool telemetry.
/// A delivery service monitors connection acquisitions and completed borrows while writing
/// and reading a delivery, and removes its pool's telemetry when the service shuts down.
public class DropwizardPoolTelemetryTest {
    @Test
    @Timeout(60)
    public void reportsDeliveryRequestsAndUnregistersMetricsOnShutdown() throws SQLException {
        MetricRegistry metrics = new MetricRegistry();
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:delivery_pool_telemetry");

        HikariConfig config = new HikariConfig();
        config.setPoolName("delivery-service");
        config.setDataSource(database);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        config.setValidationTimeout(10000);
        config.setMetricRegistry(metrics);

        try (HikariDataSource pool = new HikariDataSource(config)) {
            // Read registered telemetry, rather than creating metrics in the test.
            Timer acquisitions = metrics.getTimers().get("delivery-service.pool.Wait");
            Histogram completedBorrows = metrics.getHistograms().get("delivery-service.pool.Usage");
            assertNotNull(acquisitions);
            assertNotNull(completedBorrows);
            assertEquals(0, acquisitions.getCount());
            assertEquals(0, completedBorrows.getCount());

            try (Connection request = pool.getConnection();
                            Statement statement = request.createStatement()) {
                statement.execute("CREATE TABLE deliveries (delivery_id INTEGER PRIMARY KEY, status VARCHAR(20))");
                assertEquals(1, statement.executeUpdate(
                                "INSERT INTO deliveries (delivery_id, status) VALUES (701, 'dispatched')"));
                assertEquals(1, acquisitions.getCount());
                assertEquals(0, completedBorrows.getCount(),
                                "An active request has not yet contributed a connection usage sample");
            }
            assertEquals(1, completedBorrows.getCount());

            try (Connection request = pool.getConnection();
                            Statement query = request.createStatement();
                            ResultSet deliveries = query.executeQuery(
                                            "SELECT status FROM deliveries WHERE delivery_id = 701")) {
                assertTrue(deliveries.next());
                assertEquals("dispatched", deliveries.getString("status"));
                assertFalse(deliveries.next());
                assertEquals(2, acquisitions.getCount());
                assertEquals(1, completedBorrows.getCount());
            }
            // These counts are recorded synchronously; no rates or elapsed-time assumptions.
            assertEquals(2, acquisitions.getCount());
            assertEquals(2, completedBorrows.getCount());
        }

        assertTrue(metrics.getNames().isEmpty(), "A stopped service must not leave stale pool metrics registered");
    }
}
