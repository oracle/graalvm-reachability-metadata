/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.hikari;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/// Exercises HikariCP's Micrometer metrics integration for pool saturation monitoring.
/// A reservation service records an exhausted-pool timeout without interrupting an active
/// request, then completes another request once capacity is released.
public class MicrometerPoolSaturationTest {
    @Test
    @Timeout(60)
    public void recordsRejectedReservationAndRecoveryInApplicationMetrics() throws SQLException {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:micrometer_reservation_saturation");

        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        try {
            HikariConfig config = new HikariConfig();
            config.setPoolName("reservation-service");
            config.setDataSource(database);
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(10000);
            config.setValidationTimeout(10000);
            config.setMetricsTrackerFactory(new MicrometerMetricsTrackerFactory(metrics));

            try (HikariDataSource pool = new HikariDataSource(config)) {
                Counter timeouts = metrics.get("hikaricp.connections.timeout")
                                .tag("pool", "reservation-service").counter();
                Timer completedRequests = metrics.get("hikaricp.connections.usage")
                                .tag("pool", "reservation-service").timer();
                assertEquals(0.0, timeouts.count());
                assertEquals(0, completedRequests.count());

                // Keep the sole lease until after the rejected request and its metric are observed.
                try (Connection reservation = pool.getConnection();
                                Statement booking = reservation.createStatement()) {
                    booking.execute("CREATE TABLE reservations (reservation_id INTEGER PRIMARY KEY, status VARCHAR(20))");
                    assertEquals(1, booking.executeUpdate("INSERT INTO reservations VALUES (1001, 'pending')"));

                    assertThrows(SQLTransientConnectionException.class, () -> {
                        try (Connection rejected = pool.getConnection()) {
                            // A mistakenly admitted request still closes its lease before failing.
                            fail("The saturated pool admitted another request: " + rejected);
                        }
                    });
                    assertEquals(1.0, timeouts.count(), "The application's timeout counter must expose saturation");
                    assertEquals(0, completedRequests.count(), "The active reservation is not a completed borrow");
                    assertFalse(reservation.isClosed(), "Pool exhaustion must not cancel the active request");
                    assertEquals(1, booking.executeUpdate(
                                    "UPDATE reservations SET status = 'confirmed' WHERE reservation_id = 1001"));
                }
                assertEquals(1, completedRequests.count());

                try (Connection confirmation = pool.getConnection();
                                Statement query = confirmation.createStatement();
                                ResultSet reservation = query.executeQuery(
                                                "SELECT status FROM reservations WHERE reservation_id = 1001")) {
                    assertTrue(reservation.next());
                    assertEquals("confirmed", reservation.getString("status"));
                    assertFalse(reservation.next());
                }
                assertEquals(2, completedRequests.count(), "Both successful requests must be recorded");
                assertEquals(1.0, timeouts.count(), "A successful request must not increase the rejection counter");
            }

            assertTrue(metrics.getMeters().isEmpty(), "Pool shutdown must unregister its Micrometer meters");
        } finally {
            metrics.close();
        }
    }
}
