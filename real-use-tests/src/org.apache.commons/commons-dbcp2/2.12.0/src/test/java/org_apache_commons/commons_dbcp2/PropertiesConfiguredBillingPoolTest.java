/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_dbcp2;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbcp2.BasicDataSourceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises BasicDataSourceFactory property-based configuration and connection initialization SQL.
/// A billing application loads its pool settings from deployment properties; every physical
/// session must select the billing schema before application SQL uses unqualified table names.
public class PropertiesConfiguredBillingPoolTest {

    @Test
    @Timeout(60)
    public void deploymentPropertiesInitializeEveryBillingSession() throws Exception {
        Properties deployment = new Properties();
        try (StringReader configuration = new StringReader("""
                driverClassName=org.h2.Driver
                url=jdbc:h2:mem:properties_billing;DB_CLOSE_ON_EXIT=FALSE
                username=sa
                password=
                maxTotal=2
                maxIdle=2
                maxWaitMillis=10000
                defaultAutoCommit=true
                defaultQueryTimeout=10
                connectionInitSqls=CREATE SCHEMA IF NOT EXISTS BILLING;SET SCHEMA BILLING
                """)) {
            deployment.load(configuration);
        }

        try (BasicDataSource billing = BasicDataSourceFactory.createDataSource(deployment)) {
            try (Connection invoiceRequest = billing.getConnection()) {
                assertTrue(invoiceRequest.getAutoCommit());
                try (Statement statement = invoiceRequest.createStatement()) {
                    statement.executeUpdate("""
                            CREATE TABLE invoices (
                                id INTEGER PRIMARY KEY,
                                customer VARCHAR(80) NOT NULL,
                                total_cents INTEGER NOT NULL,
                                paid BOOLEAN NOT NULL
                            )
                            """);
                    assertEquals(1, statement.executeUpdate(
                            "INSERT INTO invoices VALUES (501, 'River Market', 2750, FALSE)"));
                }

                // Keep the first lease open so this request needs another initialized session.
                try (Connection paymentRequest = billing.getConnection()) {
                    assertTrue(paymentRequest.getAutoCommit());
                    assertInvoice(paymentRequest, false);
                    try (Statement statement = paymentRequest.createStatement()) {
                        assertEquals(1, statement.executeUpdate(
                                "UPDATE invoices SET paid = TRUE WHERE id = 501"));
                    }
                }
                assertInvoice(invoiceRequest, true);
            }

            // A later request must retain the configured schema when it reuses a pooled session.
            try (Connection receiptRequest = billing.getConnection()) {
                assertInvoice(receiptRequest, true);
            }
        }
    }

    private static void assertInvoice(Connection connection, boolean paid) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("""
                        SELECT CURRENT_SCHEMA AS schema_name, customer, total_cents, paid
                        FROM invoices WHERE id = 501
                        """)) {
            assertTrue(rows.next());
            assertEquals("BILLING", rows.getString("schema_name"));
            assertEquals("River Market", rows.getString("customer"));
            assertEquals(2750, rows.getInt("total_cents"));
            assertEquals(paid, rows.getBoolean("paid"));
            assertFalse(rows.next());
        }
    }
}
