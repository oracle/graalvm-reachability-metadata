/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_dbcp2;

import org.apache.commons.dbcp2.cpdsadapter.DriverAdapterCPDS;
import org.apache.commons.dbcp2.datasources.SharedPoolDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises DriverAdapterCPDS adaptation of a JDBC driver into pooled application connections.
/// A reservation service configures its driver by class name and URL, then uses independent
/// writer and availability-reader leases without exposing an uncommitted stock reservation.
public class DriverAdapterReservationTest {

    @Test
    @Timeout(60)
    public void adaptedDriverKeepsReservationAndAvailabilitySessionsIndependent() throws Exception {
        DriverAdapterCPDS driver = new DriverAdapterCPDS();
        driver.setDriver("org.h2.Driver");
        driver.setUrl("jdbc:h2:mem:driver_adapter_reservations;DB_CLOSE_ON_EXIT=FALSE");
        driver.setUser("sa");
        driver.setPassword("");

        try (SharedPoolDataSource reservations = new SharedPoolDataSource()) {
            reservations.setConnectionPoolDataSource(driver);
            reservations.setMaxTotal(2);
            reservations.setDefaultMaxTotal(2);
            reservations.setDefaultMaxIdle(2);
            reservations.setDefaultMaxWait(Duration.ofSeconds(10));
            reservations.setDefaultAutoCommit(true);
            reservations.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

            try (Connection setup = reservations.getConnection();
                    Statement schema = setup.createStatement()) {
                schema.executeUpdate("CREATE TABLE stock (sku VARCHAR(40) PRIMARY KEY, available INTEGER NOT NULL)");
                schema.executeUpdate("INSERT INTO stock VALUES ('notebook', 12)");
            }

            // Holding both leases forces the adapter to provide independent physical sessions.
            try (Connection writer = reservations.getConnection();
                    Connection availability = reservations.getConnection()) {
                writer.setAutoCommit(false);
                try {
                    assertTrue(reserve(writer, 3));
                    assertAvailable(writer, 9);
                    assertAvailable(availability, 12);
                    writer.commit();
                    assertAvailable(availability, 9);
                } finally {
                    writer.rollback();
                    writer.setAutoCommit(true);
                }
            }

            // Logical connection-close events return the adapted sessions for later requests.
            try (Connection nextRequest = reservations.getConnection()) {
                assertTrue(nextRequest.getAutoCommit());
                assertTrue(reserve(nextRequest, 2));
                assertFalse(reserve(nextRequest, 8), "An oversized reservation must not oversell stock");
            }
            try (Connection latestAvailability = reservations.getConnection()) {
                assertAvailable(latestAvailability, 7);
            }
        }
    }

    private static boolean reserve(Connection connection, int quantity) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE stock SET available = available - ?
                WHERE sku = ? AND available >= ?
                """)) {
            update.setInt(1, quantity);
            update.setString(2, "notebook");
            update.setInt(3, quantity);
            return update.executeUpdate() == 1;
        }
    }

    private static void assertAvailable(Connection connection, int expected) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT available FROM stock WHERE sku = ?")) {
            query.setString(1, "notebook");
            try (ResultSet rows = query.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(expected, rows.getInt("available"));
                assertFalse(rows.next());
            }
        }
    }
}
