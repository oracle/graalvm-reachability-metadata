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

/// Exercises BasicDataSource restoration of configured schema and isolation on borrowed sessions.
/// A historical report temporarily selects an archive schema and repeatable-read isolation;
/// the next billing request must use live invoices and see newly committed payments.
public class ReportingSessionDefaultsTest {

    @Test
    @Timeout(60)
    public void historicalReportSettingsDoNotLeakIntoLiveBillingRequests() throws SQLException {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:reporting_session_defaults");
        database.setUser("sa");
        database.setPassword("");

        try (Connection administrator = database.getConnection();
                BasicDataSource billing = new BasicDataSource()) {
            try (Statement setup = administrator.createStatement()) {
                setup.executeUpdate("CREATE SCHEMA LIVE");
                setup.executeUpdate("CREATE SCHEMA ARCHIVE");
                setup.executeUpdate("CREATE TABLE LIVE.invoices (id INTEGER PRIMARY KEY, unpaid_cents INTEGER)");
                setup.executeUpdate("CREATE TABLE ARCHIVE.invoices (id INTEGER PRIMARY KEY, unpaid_cents INTEGER)");
                setup.executeUpdate("INSERT INTO LIVE.invoices VALUES (101, 4200)");
                setup.executeUpdate("INSERT INTO ARCHIVE.invoices VALUES (101, 900)");
            }

            billing.setDriverClassName("org.h2.Driver");
            billing.setUrl(database.getURL());
            billing.setUsername("sa");
            billing.setPassword("");
            billing.setMaxTotal(1);
            billing.setMaxIdle(1);
            billing.setMaxWait(Duration.ofSeconds(10));
            billing.setDefaultQueryTimeout(Duration.ofSeconds(10));
            billing.setDefaultAutoCommit(true);
            billing.setDefaultSchema("LIVE");
            billing.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

            try (Connection historicalReport = billing.getConnection()) {
                historicalReport.setSchema("ARCHIVE");
                historicalReport.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                historicalReport.setAutoCommit(false);
                assertUnpaid(historicalReport, 900);
                historicalReport.commit();
                // The completed report returns its lease without manually restoring session defaults.
            }

            try (Connection liveRequest = billing.getConnection()) {
                assertEquals("LIVE", liveRequest.getSchema());
                assertEquals(Connection.TRANSACTION_READ_COMMITTED, liveRequest.getTransactionIsolation());
                liveRequest.setAutoCommit(false);
                try {
                    assertUnpaid(liveRequest, 4200);
                    // A payment worker commits between two reads in the same billing transaction.
                    try (Statement payment = administrator.createStatement()) {
                        assertEquals(1, payment.executeUpdate(
                                "UPDATE LIVE.invoices SET unpaid_cents = unpaid_cents - 1200 WHERE id = 101"));
                    }
                    assertUnpaid(liveRequest, 3000);
                    try (Statement adjustment = liveRequest.createStatement()) {
                        assertEquals(1, adjustment.executeUpdate(
                                "UPDATE invoices SET unpaid_cents = unpaid_cents - 500 WHERE id = 101"));
                    }
                    liveRequest.commit();
                } finally {
                    liveRequest.rollback();
                }
            }

            try (Connection nextRequest = billing.getConnection()) {
                assertUnpaid(nextRequest, 2500);
            }
            // Unqualified billing writes must never change the historical invoice with the same ID.
            try (Statement query = administrator.createStatement();
                    ResultSet rows = query.executeQuery("SELECT unpaid_cents FROM ARCHIVE.invoices WHERE id = 101")) {
                assertTrue(rows.next());
                assertEquals(900, rows.getInt("unpaid_cents"));
                assertFalse(rows.next());
            }
        }
    }

    private static void assertUnpaid(Connection connection, int expectedCents) throws SQLException {
        try (Statement query = connection.createStatement();
                ResultSet rows = query.executeQuery("SELECT unpaid_cents FROM invoices WHERE id = 101")) {
            assertTrue(rows.next());
            assertEquals(expectedCents, rows.getInt("unpaid_cents"));
            assertFalse(rows.next());
        }
    }
}
