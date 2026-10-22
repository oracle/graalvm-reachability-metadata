/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_dbcp2;

import org.apache.commons.dbcp2.BasicDataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises DBCP borrow-time connection validation after database maintenance closes idle sessions.
/// An invoice service must continue reading and writing committed invoices through the same pool
/// after the database administrator disconnects its existing physical connection.
public class StaleConnectionRecoveryTest {

    @Test
    @Timeout(60)
    public void invoiceRequestsRecoverAfterMaintenanceDisconnectsTheIdleSession() throws SQLException {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:invoice_connection_recovery");
        database.setUser("sa");
        database.setPassword("");

        // The administrator keeps the in-memory database alive throughout maintenance.
        try (Connection administrator = database.getConnection();
                BasicDataSource invoices = new BasicDataSource()) {
            invoices.setDriverClassName("org.h2.Driver");
            invoices.setUrl(database.getURL());
            invoices.setUsername("sa");
            invoices.setPassword("");
            invoices.setMaxTotal(1);
            invoices.setMaxIdle(1);
            invoices.setMaxWait(Duration.ofSeconds(10));
            invoices.setDefaultAutoCommit(true);
            invoices.setTestOnBorrow(true);
            invoices.setValidationQuery("SELECT 1");
            invoices.setValidationQueryTimeout(Duration.ofSeconds(10));
            invoices.setDefaultQueryTimeout(Duration.ofSeconds(10));

            try (Connection connection = invoices.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE invoices (
                            id INTEGER PRIMARY KEY,
                            customer VARCHAR(80) NOT NULL,
                            total_cents INTEGER NOT NULL
                        )
                        """);
                assertEquals(1, statement.executeUpdate(
                        "INSERT INTO invoices VALUES (1, 'Harbor Books', 4200)"));
            }

            // H2's exclusive maintenance mode synchronously closes all other sessions.
            // The stale session remains idle in DBCP until the next application request.
            try (Statement maintenance = administrator.createStatement()) {
                maintenance.execute("SET EXCLUSIVE 2");
                maintenance.execute("SET EXCLUSIVE 0");
            }

            try (Connection connection = invoices.getConnection()) {
                assertInvoice(connection, 1, "Harbor Books", 4200);
                try (Statement statement = connection.createStatement()) {
                    assertEquals(1, statement.executeUpdate(
                            "INSERT INTO invoices VALUES (2, 'Meadow Cafe', 1850)"));
                }
            }

            // A later request reuses the healthy replacement and sees both committed invoices.
            try (Connection connection = invoices.getConnection()) {
                assertInvoice(connection, 1, "Harbor Books", 4200);
                assertInvoice(connection, 2, "Meadow Cafe", 1850);
            }
        }
    }

    private static void assertInvoice(Connection connection, int id, String customer, int totalCents)
            throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT customer, total_cents FROM invoices WHERE id = " + id)) {
            assertTrue(rows.next());
            assertEquals(customer, rows.getString("customer"));
            assertEquals(totalCents, rows.getInt("total_cents"));
            assertFalse(rows.next());
        }
    }
}
