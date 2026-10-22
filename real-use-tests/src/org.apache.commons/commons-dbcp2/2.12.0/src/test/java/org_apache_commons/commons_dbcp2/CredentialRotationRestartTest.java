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

/// Exercises BasicDataSource's pool restart lifecycle during database credential rotation.
/// A receipt service replaces its pool with new credentials while an in-flight transaction
/// retains its old lease, then observes receipts committed by both generations of connections.
public class CredentialRotationRestartTest {

    @Test
    @Timeout(60)
    public void credentialRotationPreservesInFlightWorkAndAdmitsNewRequests() throws SQLException {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:receipt_credential_rotation");
        database.setUser("sa");
        database.setPassword("");

        // Keep the database alive independently of either generation of application connections.
        try (Connection administrator = database.getConnection();
                BasicDataSource receipts = new BasicDataSource()) {
            try (Statement setup = administrator.createStatement()) {
                setup.executeUpdate("CREATE USER RECEIPT_WRITER PASSWORD 'original-secret'");
                setup.executeUpdate("CREATE TABLE receipts (id INTEGER PRIMARY KEY, total_cents INTEGER NOT NULL)");
                setup.executeUpdate("GRANT SELECT, INSERT ON receipts TO RECEIPT_WRITER");
            }

            receipts.setDriverClassName("org.h2.Driver");
            receipts.setUrl(database.getURL());
            receipts.setUsername("RECEIPT_WRITER");
            receipts.setPassword("original-secret");
            receipts.setMaxTotal(1);
            receipts.setMaxIdle(1);
            receipts.setMaxWait(Duration.ofSeconds(10));
            receipts.setDefaultAutoCommit(true);
            receipts.setDefaultQueryTimeout(Duration.ofSeconds(10));

            try (Connection inFlight = receipts.getConnection()) {
                inFlight.setAutoCommit(false);
                insertReceipt(inFlight, 101, 1250);

                try (Statement rotation = administrator.createStatement()) {
                    rotation.executeUpdate("ALTER USER RECEIPT_WRITER SET PASSWORD 'replacement-secret'");
                }
                receipts.setPassword("replacement-secret");
                receipts.restart();

                // Even with maxTotal=1 and the old lease held, the restarted pool serves new work.
                try (Connection nextRequest = receipts.getConnection()) {
                    insertReceipt(nextRequest, 102, 2300);
                }

                // Restart must not abort an already accepted request or close its transaction.
                insertReceipt(inFlight, 103, 750);
                inFlight.commit();
            }

            try (Connection report = receipts.getConnection();
                    Statement query = report.createStatement();
                    ResultSet rows = query.executeQuery("SELECT id, total_cents FROM receipts ORDER BY id")) {
                assertTrue(rows.next());
                assertEquals(101, rows.getInt("id"));
                assertEquals(1250, rows.getInt("total_cents"));
                assertTrue(rows.next());
                assertEquals(102, rows.getInt("id"));
                assertEquals(2300, rows.getInt("total_cents"));
                assertTrue(rows.next());
                assertEquals(103, rows.getInt("id"));
                assertEquals(750, rows.getInt("total_cents"));
                assertFalse(rows.next());
            }
        }
    }

    private static void insertReceipt(Connection connection, int id, int totalCents) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO receipts VALUES (?, ?)")) {
            insert.setInt(1, id);
            insert.setInt(2, totalCents);
            assertEquals(1, insert.executeUpdate());
        }
    }
}
