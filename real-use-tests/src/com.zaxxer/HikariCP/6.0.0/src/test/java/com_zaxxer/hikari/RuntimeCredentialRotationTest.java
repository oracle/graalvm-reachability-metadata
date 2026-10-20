/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.hikari;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.util.Credentials;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises HikariCP's runtime credential-management subsystem for database account rotation.
/// A billing service replaces its username and password together without restarting the pool;
/// an in-flight request finishes, and the next request authenticates as the replacement account.
public class RuntimeCredentialRotationTest {
    @Test
    @Timeout(60)
    public void replacesTheCredentialPairAndContinuesBillingWithTheNewAccount() throws SQLException {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:billing_credential_rotation");
        database.setUser("sa");
        database.setPassword("");

        // The administrator keeps the database alive while the application replaces sessions.
        try (Connection administrator = database.getConnection()) {
            try (Statement provisioning = administrator.createStatement()) {
                provisioning.execute("CREATE TABLE billing_jobs (job_id INTEGER PRIMARY KEY, status VARCHAR(20))");
                provisioning.execute("CREATE USER billing_v1 PASSWORD 'initial-secret'");
                provisioning.execute("GRANT SELECT, INSERT, UPDATE ON billing_jobs TO billing_v1");
            }

            HikariConfig config = new HikariConfig();
            config.setDataSource(database);
            config.setCredentials(Credentials.of("billing_v1", "initial-secret"));
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(1);
            config.setMaxLifetime(0);
            config.setKeepaliveTime(0);
            config.setConnectionTimeout(10000);
            config.setValidationTimeout(10000);

            try (HikariDataSource pool = new HikariDataSource(config)) {
                try (Connection currentRequest = pool.getConnection()) {
                    assertDatabaseUser(currentRequest, "BILLING_V1");
                    try (Statement billing = currentRequest.createStatement()) {
                        assertEquals(1, billing.executeUpdate("INSERT INTO billing_jobs VALUES (901, 'pending')"));
                    }

                    // A deployment provisions the replacement identity and revokes the old secret.
                    try (Statement rotation = administrator.createStatement()) {
                        rotation.execute("CREATE USER billing_v2 PASSWORD 'replacement-secret'");
                        rotation.execute("GRANT SELECT, INSERT, UPDATE ON billing_jobs TO billing_v2");
                        rotation.execute("ALTER USER billing_v1 SET PASSWORD 'revoked-secret'");
                    }
                    pool.getHikariConfigMXBean().setCredentials(
                                    Credentials.of("billing_v2", "replacement-secret"));
                    pool.getHikariPoolMXBean().softEvictConnections();

                    // Changing credentials affects new logins, not work already authenticated.
                    assertDatabaseUser(currentRequest, "BILLING_V1");
                    try (Statement billing = currentRequest.createStatement()) {
                        assertEquals(1, billing.executeUpdate(
                                        "UPDATE billing_jobs SET status = 'invoiced' WHERE job_id = 901"));
                    }
                }

                // The retired lease must be replaced using both parts of the new credential pair.
                try (Connection nextRequest = pool.getConnection()) {
                    assertDatabaseUser(nextRequest, "BILLING_V2");
                    try (Statement query = nextRequest.createStatement();
                                    ResultSet jobs = query.executeQuery("SELECT job_id, status FROM billing_jobs")) {
                        assertTrue(jobs.next());
                        assertEquals(901, jobs.getInt("job_id"));
                        assertEquals("invoiced", jobs.getString("status"));
                        assertFalse(jobs.next());
                    }
                }
            }
        }
    }

    private static void assertDatabaseUser(Connection connection, String expectedUser) throws SQLException {
        try (Statement query = connection.createStatement();
                        ResultSet identity = query.executeQuery("SELECT CURRENT_USER")) {
            assertTrue(identity.next());
            assertEquals(expectedUser, identity.getString(1));
            assertFalse(identity.next());
        }
    }
}
