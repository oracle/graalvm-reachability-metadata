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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises BasicDataSource explicit connection invalidation for sensitive reporting sessions.
/// Each report stages private data in a session-local temporary table and discards its session
/// rather than returning that data to the pool; committed audit records survive the replacement.
public class SensitiveReportSessionInvalidationTest {

    @Test
    @Timeout(60)
    public void discardingPrivateReportSessionsPreservesAuditsAndIsolatesTheNextReport() throws SQLException {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:sensitive_report_invalidation");
        database.setUser("sa");
        database.setPassword("");

        // Keep committed audit data alive independently of the disposable application sessions.
        try (Connection administrator = database.getConnection();
                BasicDataSource reports = new BasicDataSource()) {
            try (Statement setup = administrator.createStatement()) {
                setup.executeUpdate("CREATE TABLE report_audit (id INTEGER PRIMARY KEY, total_cents INTEGER)");
            }
            reports.setDriverClassName("org.h2.Driver");
            reports.setUrl(database.getURL());
            reports.setUsername("sa");
            reports.setPassword("");
            reports.setMaxTotal(1);
            reports.setMaxIdle(1);
            reports.setMaxWait(Duration.ofSeconds(10));
            reports.setDefaultAutoCommit(true);
            reports.setDefaultQueryTimeout(Duration.ofSeconds(10));

            assertEquals(4200, runPrivateReport(reports, 101, "Northwind Books", 4200));
            // Recreating the same session-local table must succeed, with none of the prior data.
            assertEquals(1850, runPrivateReport(reports, 102, "Meadow Cafe", 1850));

            // Invalidation releases the single pool slot so ordinary application work still runs.
            try (Connection receipt = reports.getConnection();
                    Statement query = receipt.createStatement();
                    ResultSet rows = query.executeQuery("SELECT id, total_cents FROM report_audit ORDER BY id")) {
                assertTrue(rows.next());
                assertEquals(101, rows.getInt("id"));
                assertEquals(4200, rows.getInt("total_cents"));
                assertTrue(rows.next());
                assertEquals(102, rows.getInt("id"));
                assertEquals(1850, rows.getInt("total_cents"));
                assertFalse(rows.next());
            }
        }
    }

    private static int runPrivateReport(BasicDataSource reports, int reportId, String customer, int cents)
            throws SQLException {
        try (Connection connection = reports.getConnection()) {
            try {
                try (Statement setup = connection.createStatement()) {
                    setup.executeUpdate("""
                            CREATE LOCAL TEMPORARY TABLE private_report (
                                customer VARCHAR(80) NOT NULL,
                                total_cents INTEGER NOT NULL
                            )
                            """);
                }
                try (PreparedStatement stage = connection.prepareStatement("INSERT INTO private_report VALUES (?, ?)")) {
                    stage.setString(1, customer);
                    stage.setInt(2, cents);
                    assertEquals(1, stage.executeUpdate());
                }
                int total;
                try (Statement query = connection.createStatement();
                        ResultSet rows = query.executeQuery("SELECT customer, total_cents FROM private_report")) {
                    assertTrue(rows.next());
                    assertEquals(customer, rows.getString("customer"));
                    total = rows.getInt("total_cents");
                    assertFalse(rows.next(), "A report must contain only its own private data");
                }
                try (PreparedStatement audit = connection.prepareStatement("INSERT INTO report_audit VALUES (?, ?)")) {
                    audit.setInt(1, reportId);
                    audit.setInt(2, total);
                    assertEquals(1, audit.executeUpdate());
                }
                return total;
            } finally {
                // Closing a lease alone leaves temporary tables in its reusable physical session.
                reports.invalidateConnection(connection);
            }
        }
    }
}
