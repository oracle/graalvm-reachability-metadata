/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_dbcp2;

import org.apache.commons.dbcp2.datasources.PerUserPoolDataSource;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises PerUserPoolDataSource credential-specific pools for inventory writers and reporting users.
/// Reports borrow a read-only database identity while stock updates use a separate identity, even
/// when the writer's single-connection pool is fully occupied and reporting leases are reused.
public class PerUserReportingPoolTest {

    @Test
    @Timeout(60)
    public void reportingLeasesRetainTheirOwnIdentityAndPermissions() throws SQLException {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:per_user_inventory");
        // Keep the bootstrap connection open so closing an application lease cannot drop the database.
        try (Connection administrator = database.getConnection("sa", "");
                PerUserPoolDataSource dataSource = new PerUserPoolDataSource()) {
            try (Statement statement = administrator.createStatement()) {
                statement.executeUpdate("CREATE USER STOCK_WRITER PASSWORD 'writer-secret'");
                statement.executeUpdate("CREATE USER REPORTER PASSWORD 'report-secret'");
                statement.executeUpdate("CREATE TABLE inventory (sku VARCHAR(40) PRIMARY KEY, quantity INTEGER)");
                statement.executeUpdate("INSERT INTO inventory VALUES ('notebook', 12)");
                statement.executeUpdate("GRANT SELECT, UPDATE ON inventory TO STOCK_WRITER");
                statement.executeUpdate("GRANT SELECT ON inventory TO REPORTER");
            }

            dataSource.setConnectionPoolDataSource(database);
            dataSource.setDefaultMaxTotal(1);
            dataSource.setDefaultMaxIdle(1);
            dataSource.setDefaultMaxWait(Duration.ofSeconds(10));
            dataSource.setDefaultAutoCommit(true);

            try (Connection writer = dataSource.getConnection("STOCK_WRITER", "writer-secret")) {
                assertIdentity(writer, "STOCK_WRITER");
                // A writer lease must not consume the reporting user's separate pool capacity.
                try (Connection reporter = dataSource.getConnection("REPORTER", "report-secret")) {
                    assertReport(reporter, 12);
                }
                try (Statement statement = writer.createStatement()) {
                    assertEquals(1, statement.executeUpdate(
                            "UPDATE inventory SET quantity = quantity - 3 WHERE sku = 'notebook'"));
                }
                try (Connection reporter = dataSource.getConnection("REPORTER", "report-secret")) {
                    assertReport(reporter, 9);
                    try (Statement statement = reporter.createStatement()) {
                        assertThrows(SQLException.class, () -> statement.executeUpdate(
                                "UPDATE inventory SET quantity = 0 WHERE sku = 'notebook'"));
                    }
                }
            }

            // Returning both credentials' leases must not mix their identities on the next request.
            try (Connection reporter = dataSource.getConnection("REPORTER", "report-secret");
                    Connection writer = dataSource.getConnection("STOCK_WRITER", "writer-secret")) {
                assertIdentity(writer, "STOCK_WRITER");
                assertReport(reporter, 9);
            }
        }
    }

    private static void assertIdentity(Connection connection, String expectedUser) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT CURRENT_USER")) {
            assertTrue(rows.next());
            assertEquals(expectedUser, rows.getString(1));
            assertFalse(rows.next());
        }
    }

    private static void assertReport(Connection connection, int expectedQuantity) throws SQLException {
        assertIdentity(connection, "REPORTER");
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT sku, quantity FROM inventory")) {
            assertTrue(rows.next());
            assertEquals("notebook", rows.getString("sku"));
            assertEquals(expectedQuantity, rows.getInt("quantity"));
            assertFalse(rows.next());
        }
    }
}
