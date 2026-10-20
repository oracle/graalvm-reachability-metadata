/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.hikari;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.prometheus.PrometheusMetricsTrackerFactory;
import io.prometheus.client.CollectorRegistry;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises HikariCP's Prometheus metrics integration for independently monitored application pools.
/// A ticketing service shares a metrics registry between its sales and reporting pools;
/// stopping sales removes only its labeled samples while reporting continues serving requests.
public class PrometheusMultiPoolMonitoringTest {
    @Test
    @Timeout(60)
    public void keepsPoolMetricsSeparateWhileSalesStopsAndReportingContinues() throws SQLException {
        CollectorRegistry scrapeRegistry = new CollectorRegistry();
        PrometheusMetricsTrackerFactory telemetry = new PrometheusMetricsTrackerFactory(scrapeRegistry);
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:prometheus_ticketing_pools");

        try (HikariDataSource reporting = new HikariDataSource(poolConfig(database, telemetry, "ticket-reports"))) {
            try (HikariDataSource sales = new HikariDataSource(poolConfig(database, telemetry, "ticket-sales"))) {
                assertSample(scrapeRegistry, "hikaricp_connection_acquired_nanos_count", "ticket-sales", 0);
                assertSample(scrapeRegistry, "hikaricp_connection_acquired_nanos_count", "ticket-reports", 0);

                try (Connection sale = sales.getConnection();
                                Statement booking = sale.createStatement()) {
                    booking.execute("CREATE TABLE tickets (ticket_id INTEGER PRIMARY KEY, cents INTEGER NOT NULL)");
                    assertEquals(1, booking.executeUpdate("INSERT INTO tickets VALUES (1101, 2500)"));
                    assertSample(scrapeRegistry, "hikaricp_connection_acquired_nanos_count", "ticket-sales", 1);
                    assertSample(scrapeRegistry, "hikaricp_connection_usage_millis_count", "ticket-sales", 0);

                    // A scrape distinguishes the still-active sale from the completed report.
                    assertTicketRevenue(reporting);
                    assertSample(scrapeRegistry, "hikaricp_connection_usage_millis_count", "ticket-reports", 1);
                    assertSample(scrapeRegistry, "hikaricp_connection_usage_millis_count", "ticket-sales", 0);
                }
                assertSample(scrapeRegistry, "hikaricp_connection_usage_millis_count", "ticket-sales", 1);
                assertSample(scrapeRegistry, "hikaricp_connection_acquired_nanos_count", "ticket-reports", 1);
            }

            assertNull(scrapeRegistry.getSampleValue("hikaricp_connection_acquired_nanos_count",
                            new String[]{"pool"}, new String[]{"ticket-sales"}),
                            "Stopping sales must remove its acquisition time series");
            assertNull(scrapeRegistry.getSampleValue("hikaricp_connection_usage_millis_count",
                            new String[]{"pool"}, new String[]{"ticket-sales"}),
                            "Stopping sales must remove its usage time series");
            assertSample(scrapeRegistry, "hikaricp_connection_usage_millis_count", "ticket-reports", 1);

            assertTicketRevenue(reporting);
            assertSample(scrapeRegistry, "hikaricp_connection_acquired_nanos_count", "ticket-reports", 2);
            assertSample(scrapeRegistry, "hikaricp_connection_usage_millis_count", "ticket-reports", 2);
        } finally {
            scrapeRegistry.clear();
        }
    }

    private static HikariConfig poolConfig(JdbcDataSource database,
                    PrometheusMetricsTrackerFactory telemetry, String poolName) {
        HikariConfig config = new HikariConfig();
        config.setDataSource(database);
        config.setPoolName(poolName);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        config.setValidationTimeout(10000);
        config.setMetricsTrackerFactory(telemetry);
        return config;
    }

    private static void assertTicketRevenue(HikariDataSource reporting) throws SQLException {
        try (Connection report = reporting.getConnection();
                        Statement query = report.createStatement();
                        ResultSet revenue = query.executeQuery("SELECT COUNT(*), SUM(cents) FROM tickets")) {
            assertTrue(revenue.next());
            assertEquals(1, revenue.getInt(1));
            assertEquals(2500, revenue.getInt(2));
            assertFalse(revenue.next());
        }
    }

    private static void assertSample(CollectorRegistry registry, String metric, String poolName, double expected) {
        Double value = registry.getSampleValue(metric, new String[]{"pool"}, new String[]{poolName});
        assertNotNull(value, "The monitoring scrape must include " + metric + " for " + poolName);
        assertEquals(expected, value.doubleValue());
    }
}
