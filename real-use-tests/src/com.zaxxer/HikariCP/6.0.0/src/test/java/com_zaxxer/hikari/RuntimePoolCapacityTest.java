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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises HikariCP's runtime pool-capacity reconfiguration subsystem.
/// An inventory service expands its pool during a demand surge so a replenishment request
/// can commit while a reporting request keeps its original database connection.
public class RuntimePoolCapacityTest {
    @Test
    @Timeout(60)
    public void expandsCapacityWithoutDisconnectingAnActiveRequest() throws SQLException {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:runtime_pool_capacity");

        HikariConfig config = new HikariConfig();
        config.setDataSource(database);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setMaxLifetime(0);
        config.setKeepaliveTime(0);
        config.setConnectionTimeout(10000);
        config.setValidationTimeout(10000);

        try (Connection databaseLifetime = database.getConnection();
                        HikariDataSource pool = new HikariDataSource(config)) {
            try (Statement schema = databaseLifetime.createStatement()) {
                schema.execute("CREATE TABLE inventory (sku VARCHAR(30) PRIMARY KEY, quantity INTEGER NOT NULL)");
                schema.execute("INSERT INTO inventory VALUES ('COFFEE-250G', 10)");
            }

            try (Connection report = pool.getConnection()) {
                int reportingSession = sessionId(report);
                assertQuantity(report, 10);

                // The operator changes the running pool, not a replacement datasource.
                pool.getHikariConfigMXBean().setMaximumPoolSize(2);

                // Keep the report's connection borrowed: the new request needs real extra capacity.
                try (Connection replenishment = pool.getConnection()) {
                    assertNotEquals(reportingSession, sessionId(replenishment),
                                    "Overlapping requests must have independent database sessions");
                    replenishment.setAutoCommit(false);
                    try (Statement update = replenishment.createStatement()) {
                        assertEquals(1, update.executeUpdate("""
                                UPDATE inventory SET quantity = quantity + 5
                                WHERE sku = 'COFFEE-250G'
                                """));
                    }
                    replenishment.commit();
                }

                assertFalse(report.isClosed(), "Resizing must not disconnect an in-flight report");
                assertEquals(reportingSession, sessionId(report));
                assertQuantity(report, 15);
            }

            try (Connection nextRequest = pool.getConnection()) {
                assertQuantity(nextRequest, 15);
            }
        }
    }

    private static int sessionId(Connection connection) throws SQLException {
        try (Statement query = connection.createStatement();
                        ResultSet session = query.executeQuery("SELECT SESSION_ID()")) {
            assertTrue(session.next());
            return session.getInt(1);
        }
    }

    private static void assertQuantity(Connection connection, int expected) throws SQLException {
        try (Statement query = connection.createStatement();
                        ResultSet inventory = query.executeQuery(
                                        "SELECT quantity FROM inventory WHERE sku = 'COFFEE-250G'")) {
            assertTrue(inventory.next());
            assertEquals(expected, inventory.getInt("quantity"));
            assertFalse(inventory.next());
        }
    }
}
