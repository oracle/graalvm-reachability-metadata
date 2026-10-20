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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/// Exercises HikariCP's orderly pool shutdown and physical-connection release subsystem.
/// A job service finishes its request before stopping, releases its database session,
/// and restarts with a new pool that continues processing the persisted queue.
public class ServicePoolShutdownTest {
    @Test
    @Timeout(60)
    public void releasesDatabaseSessionAndRejectsRequestsAfterServiceShutdown() throws SQLException {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:service_pool_shutdown");

        // The deployment connection keeps the database alive across service restarts.
        try (Connection deployment = database.getConnection()) {
            try (Statement migration = deployment.createStatement()) {
                migration.execute("CREATE TABLE jobs (job_id INTEGER PRIMARY KEY, status VARCHAR(20))");
                migration.execute("INSERT INTO jobs VALUES (1501, 'pending'), (1502, 'pending')");
            }

            HikariConfig config = new HikariConfig();
            config.setDataSource(database);
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(1);
            config.setMaxLifetime(0);
            config.setKeepaliveTime(0);
            config.setConnectionTimeout(10000);
            config.setValidationTimeout(10000);

            int originalSession;
            try (HikariDataSource service = new HikariDataSource(config)) {
                try (Connection completedJob = service.getConnection()) {
                    originalSession = sessionId(completedJob);
                    completedJob.setAutoCommit(false);
                    completeJob(completedJob, 1501);
                    completedJob.commit();
                }

                // Returning a lease keeps the session available for subsequent requests.
                assertSessionCount(deployment, originalSession, 1);
                assertStatuses(deployment, "done", "pending");

                // The service drains requests before closing its application-owned pool.
                service.close();
                assertTrue(service.isClosed());
                assertFalse(service.isRunning());
                assertSessionCount(deployment, originalSession, 0);
                assertThrows(SQLException.class, () -> {
                    try (Connection rejected = service.getConnection()) {
                        fail("A stopped service must reject database requests: " + rejected);
                    }
                });
            }

            // Restarting the service creates a new pool, not a reopened stopped datasource.
            try (HikariDataSource replacement = new HikariDataSource(config);
                            Connection nextJob = replacement.getConnection()) {
                assertNotEquals(originalSession, sessionId(nextJob));
                assertStatuses(nextJob, "done", "pending");
                nextJob.setAutoCommit(false);
                completeJob(nextJob, 1502);
                nextJob.commit();
                assertStatuses(deployment, "done", "done");
                assertSessionCount(deployment, originalSession, 0);
            }
        }
    }

    private static void completeJob(Connection connection, int jobId) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("UPDATE jobs SET status = 'done' WHERE job_id = ?")) {
            update.setInt(1, jobId);
            assertEquals(1, update.executeUpdate());
        }
    }

    private static int sessionId(Connection connection) throws SQLException {
        try (Statement query = connection.createStatement();
                        ResultSet session = query.executeQuery("SELECT SESSION_ID()")) {
            assertTrue(session.next());
            return session.getInt(1);
        }
    }

    private static void assertSessionCount(Connection administrator, int sessionId, int expected) throws SQLException {
        try (PreparedStatement query = administrator.prepareStatement(
                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE SESSION_ID = ?")) {
            query.setInt(1, sessionId);
            try (ResultSet sessions = query.executeQuery()) {
                assertTrue(sessions.next());
                assertEquals(expected, sessions.getInt(1), "Shutdown must release the actual database session");
                assertFalse(sessions.next());
            }
        }
    }

    private static void assertStatuses(Connection connection, String first, String second) throws SQLException {
        try (Statement query = connection.createStatement();
                        ResultSet jobs = query.executeQuery("SELECT job_id, status FROM jobs ORDER BY job_id")) {
            assertTrue(jobs.next());
            assertEquals(1501, jobs.getInt("job_id"));
            assertEquals(first, jobs.getString("status"));
            assertTrue(jobs.next());
            assertEquals(1502, jobs.getInt("job_id"));
            assertEquals(second, jobs.getString("status"));
            assertFalse(jobs.next());
        }
    }
}
