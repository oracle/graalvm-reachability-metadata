/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.hikari;

import com.zaxxer.hikari.HikariDataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/// Exercises HikariCP's lazy pool initialization and startup-retry subsystem.
/// A notification service is wired before its database exists, rejects an early request,
/// and retries successfully on the same datasource after deployment provisions the database.
public class LazyPoolStartupRecoveryTest {
    @Test
    @Timeout(60)
    public void retriesStartupAfterDatabaseProvisioningWithoutReplacingTheDatasource() throws SQLException {
        String databaseUrl = "jdbc:h2:mem:notification_startup_recovery";
        JdbcDataSource applicationDatabase = new JdbcDataSource();
        applicationDatabase.setURL(databaseUrl + ";IFEXISTS=TRUE");

        // The no-argument constructor defers pool startup until the first application borrow.
        try (HikariDataSource pool = new HikariDataSource()) {
            pool.setDataSource(applicationDatabase);
            pool.setMaximumPoolSize(1);
            pool.setMinimumIdle(1);
            pool.setConnectionTimeout(10000);
            pool.setValidationTimeout(10000);
            pool.setInitializationFailTimeout(10000);
            assertFalse(pool.isRunning());

            // IFEXISTS prevents an early request from silently creating an empty database.
            assertThrows(SQLException.class, () -> {
                try (Connection prematureRequest = pool.getConnection()) {
                    fail("An unprovisioned database must reject the request: " + prematureRequest);
                }
            });
            assertFalse(pool.isRunning(), "A failed startup must not advertise a running pool");
            assertFalse(pool.isClosed(), "A failed startup must leave the datasource available for retry");

            JdbcDataSource provisioningDatabase = new JdbcDataSource();
            provisioningDatabase.setURL(databaseUrl);
            // Keep the provisioned in-memory database alive through the application's retry.
            try (Connection deployment = provisioningDatabase.getConnection()) {
                try (Statement migration = deployment.createStatement()) {
                    migration.execute("CREATE TABLE notifications (id INTEGER PRIMARY KEY, recipient VARCHAR(100))");
                }

                // No datasource replacement or configuration change is needed after provisioning.
                try (Connection request = pool.getConnection();
                                PreparedStatement notification = request.prepareStatement(
                                                "INSERT INTO notifications VALUES (?, ?)")) {
                    assertTrue(pool.isRunning());
                    notification.setInt(1, 1301);
                    notification.setString(2, "alice@example.test");
                    assertEquals(1, notification.executeUpdate());
                }

                try (Connection nextRequest = pool.getConnection();
                                Statement query = nextRequest.createStatement();
                                ResultSet notifications = query.executeQuery("SELECT id, recipient FROM notifications")) {
                    assertTrue(notifications.next());
                    assertEquals(1301, notifications.getInt("id"));
                    assertEquals("alice@example.test", notifications.getString("recipient"));
                    assertFalse(notifications.next(), "Only the request after provisioning may persist a notification");
                }
            }
        }
    }
}
