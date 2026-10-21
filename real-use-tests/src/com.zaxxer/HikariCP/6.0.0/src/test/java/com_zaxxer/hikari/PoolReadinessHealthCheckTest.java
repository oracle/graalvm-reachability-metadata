/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.hikari;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises HikariCP's Dropwizard health-check integration for application readiness.
/// A booking service reports unavailable database capacity while its sole connection is
/// borrowed, then becomes ready again after the request releases that connection.
public class PoolReadinessHealthCheckTest {
    @Test
    @Timeout(60)
    public void readinessRecoversAfterAnInFlightBookingReleasesPoolCapacity() throws SQLException {
        HealthCheckRegistry readiness = new HealthCheckRegistry();
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:booking_readiness");

        HikariConfig config = new HikariConfig();
        config.setPoolName("booking-service");
        config.setDataSource(database);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        config.setValidationTimeout(10000);
        config.setHealthCheckRegistry(readiness);
        config.addHealthCheckProperty("connectivityCheckTimeoutMs", "10000");

        try (HikariDataSource pool = new HikariDataSource(config)) {
            String checkName = "booking-service.pool.ConnectivityCheck";
            assertTrue(readiness.getNames().contains(checkName),
                            "The application readiness registry must contain the pool's connectivity check");
            assertTrue(readiness.runHealthCheck(checkName).isHealthy());

            try (Connection bookingRequest = pool.getConnection();
                            Statement booking = bookingRequest.createStatement()) {
                booking.execute("CREATE TABLE bookings (booking_id INTEGER PRIMARY KEY, status VARCHAR(20))");
                assertEquals(1, booking.executeUpdate("INSERT INTO bookings VALUES (801, 'confirmed')"));

                // The lease stays open throughout the probe, so exhaustion is not a timing race.
                HealthCheck.Result saturated = readiness.runHealthCheck(checkName);
                assertFalse(saturated.isHealthy(), "Readiness must reject traffic when no connection is available");
                assertInstanceOf(SQLException.class, saturated.getError());
                assertFalse(bookingRequest.isClosed(), "A readiness probe must not interrupt an active booking");
            }

            assertTrue(readiness.runHealthCheck(checkName).isHealthy(),
                            "Releasing the connection must restore application readiness");

            // A subsequent request also proves the probe returned its own borrowed connection.
            try (Connection confirmationRequest = pool.getConnection();
                            Statement query = confirmationRequest.createStatement();
                            ResultSet confirmation = query.executeQuery("SELECT booking_id, status FROM bookings")) {
                assertTrue(confirmation.next());
                assertEquals(801, confirmation.getInt("booking_id"));
                assertEquals("confirmed", confirmation.getString("status"));
                assertFalse(confirmation.next());
            }
        }
    }
}
