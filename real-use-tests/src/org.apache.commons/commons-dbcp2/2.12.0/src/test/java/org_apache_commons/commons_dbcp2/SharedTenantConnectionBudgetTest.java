/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_dbcp2;

import org.apache.commons.dbcp2.datasources.SharedPoolDataSource;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises SharedPoolDataSource's global connection budget across tenant credentials.
/// A tenant-aware job service rejects work when another tenant occupies the shared pool,
/// then retries with the correct database identity and schema after that lease is returned.
public class SharedTenantConnectionBudgetTest {

    @Test
    @Timeout(60)
    public void tenantsShareCapacityWithoutSharingDatabaseIdentities() throws SQLException {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:shared_tenant_jobs");
        database.setUser("sa");
        database.setPassword("");

        // Keep tenant data alive while the shared pool replaces one credential's idle session.
        try (Connection administrator = database.getConnection();
                SharedPoolDataSource jobs = new SharedPoolDataSource()) {
            try (Statement setup = administrator.createStatement()) {
                setup.executeUpdate("CREATE USER NORTH PASSWORD 'north-secret'");
                setup.executeUpdate("CREATE USER SOUTH PASSWORD 'south-secret'");
                setup.executeUpdate("CREATE SCHEMA NORTH AUTHORIZATION NORTH");
                setup.executeUpdate("CREATE SCHEMA SOUTH AUTHORIZATION SOUTH");
                setup.executeUpdate("CREATE TABLE NORTH.jobs (id INTEGER PRIMARY KEY, description VARCHAR(80))");
                setup.executeUpdate("CREATE TABLE SOUTH.jobs (id INTEGER PRIMARY KEY, description VARCHAR(80))");
                setup.executeUpdate("GRANT SELECT, INSERT ON NORTH.jobs TO NORTH");
                setup.executeUpdate("GRANT SELECT, INSERT ON SOUTH.jobs TO SOUTH");
            }

            jobs.setConnectionPoolDataSource(database);
            jobs.setMaxTotal(1);
            jobs.setDefaultMaxTotal(1);
            jobs.setDefaultMaxIdle(1);
            jobs.setDefaultBlockWhenExhausted(false);
            jobs.setDefaultMaxWait(Duration.ofSeconds(10));
            jobs.setDefaultAutoCommit(true);

            try (Connection north = jobs.getConnection("NORTH", "north-secret")) {
                north.setSchema("NORTH");
                insertJob(north, 1, "Pack northern shipment");

                // The limit applies across credentials, not independently to each tenant.
                assertThrows(SQLException.class, () -> submitSouthernJob(jobs));
                assertTenantJobs(north, "NORTH", "Pack northern shipment");
            }

            // Changing credentials must make room for a new physical session, not reuse NORTH's identity.
            submitSouthernJob(jobs);
            try (Connection south = jobs.getConnection("SOUTH", "south-secret")) {
                south.setSchema("SOUTH");
                assertTenantJobs(south, "SOUTH", "Pack southern shipment");
                try (Statement query = south.createStatement()) {
                    assertThrows(SQLException.class, () -> query.executeQuery("SELECT * FROM NORTH.jobs"));
                }
            }

            // Switching back must preserve NORTH's committed work and its database permissions.
            try (Connection north = jobs.getConnection("NORTH", "north-secret")) {
                north.setSchema("NORTH");
                assertTenantJobs(north, "NORTH", "Pack northern shipment");
                try (Statement query = north.createStatement()) {
                    assertThrows(SQLException.class, () -> query.executeQuery("SELECT * FROM SOUTH.jobs"));
                }
            }
        }
    }

    private static void submitSouthernJob(SharedPoolDataSource jobs) throws SQLException {
        try (Connection south = jobs.getConnection("SOUTH", "south-secret")) {
            south.setSchema("SOUTH");
            insertJob(south, 1, "Pack southern shipment");
        }
    }

    private static void insertJob(Connection connection, int id, String description) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO jobs VALUES (?, ?)")) {
            insert.setInt(1, id);
            insert.setString(2, description);
            assertEquals(1, insert.executeUpdate());
        }
    }

    private static void assertTenantJobs(Connection connection, String tenant, String description)
            throws SQLException {
        try (Statement query = connection.createStatement();
                ResultSet rows = query.executeQuery("SELECT CURRENT_USER AS tenant, id, description FROM jobs")) {
            assertTrue(rows.next());
            assertEquals(tenant, rows.getString("tenant"));
            assertEquals(1, rows.getInt("id"));
            assertEquals(description, rows.getString("description"));
            assertFalse(rows.next(), "Rejected requests must not create jobs");
        }
    }
}
