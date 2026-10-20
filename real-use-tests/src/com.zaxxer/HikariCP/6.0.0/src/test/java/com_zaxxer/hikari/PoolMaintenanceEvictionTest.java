/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.hikari;

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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises HikariCP's soft-eviction subsystem for graceful pool maintenance.
/// A running invoice request finishes on its borrowed connection while maintenance retires
/// that session; the next request uses a new session and still sees the completed invoice.
public class PoolMaintenanceEvictionTest {
    @Test
    @Timeout(60)
    public void retiresBorrowedSessionOnlyAfterTheRequestFinishes() throws SQLException {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:invoice_pool_maintenance");

        HikariConfig config = new HikariConfig();
        config.setDataSource(database);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setMaxLifetime(0);
        config.setKeepaliveTime(0);
        config.setConnectionTimeout(10000);
        config.setValidationTimeout(10000);

        // Keep the in-memory database alive while the pool replaces its sole session.
        try (Connection databaseLifetime = database.getConnection();
                        HikariDataSource pool = new HikariDataSource(config)) {
            try (Statement schema = databaseLifetime.createStatement()) {
                schema.execute("CREATE TABLE invoices (invoice_id INTEGER PRIMARY KEY, status VARCHAR(20))");
            }

            int retiringSession;
            try (Connection request = pool.getConnection()) {
                retiringSession = sessionId(request);

                // The administrator drains the pool without interrupting an active request.
                pool.getHikariPoolMXBean().softEvictConnections();

                assertFalse(request.isClosed());
                assertEquals(retiringSession, sessionId(request));
                try (Statement completion = request.createStatement()) {
                    assertEquals(1, completion.executeUpdate(
                                    "INSERT INTO invoices (invoice_id, status) VALUES (401, 'issued')"));
                }
            }

            // Borrowing waits for replacement; there is no timing-dependent housekeeping poll.
            try (Connection nextRequest = pool.getConnection()) {
                assertNotEquals(retiringSession, sessionId(nextRequest),
                                "Maintenance must replace the physical session, not just its JDBC wrapper");
                try (Statement query = nextRequest.createStatement();
                                ResultSet invoices = query.executeQuery(
                                                "SELECT invoice_id, status FROM invoices")) {
                    assertTrue(invoices.next());
                    assertEquals(401, invoices.getInt("invoice_id"));
                    assertEquals("issued", invoices.getString("status"));
                    assertFalse(invoices.next());
                }
            }
        }
    }

    private static int sessionId(Connection connection) throws SQLException {
        try (Statement query = connection.createStatement();
                        ResultSet session = query.executeQuery("SELECT SESSION_ID()")) {
            assertTrue(session.next());
            return session.getInt(1);
        }
    }
}
