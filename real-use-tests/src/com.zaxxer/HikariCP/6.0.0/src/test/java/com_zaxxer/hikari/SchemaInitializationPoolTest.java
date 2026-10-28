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
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises HikariCP's connection-initialization SQL subsystem for application schema selection.
/// A fulfillment service selects its schema on every new physical connection so requests
/// can use unqualified table names without accidentally reading the public schema's data.
public class SchemaInitializationPoolTest {
    @Test
    @Timeout(60)
    public void initializesEachRequestConnectionWithTheApplicationSchema() throws SQLException {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:fulfillment_schema_initialization");

        // A migration connection also keeps the database alive for the application's pool.
        try (Connection migration = database.getConnection()) {
            try (Statement schema = migration.createStatement()) {
                schema.execute("CREATE SCHEMA fulfillment");
                schema.execute("CREATE TABLE fulfillment.shipments (shipment_id INTEGER PRIMARY KEY, status VARCHAR(20))");
                schema.execute("INSERT INTO fulfillment.shipments VALUES (501, 'ready')");
                schema.execute("CREATE TABLE public.shipments (shipment_id INTEGER PRIMARY KEY, status VARCHAR(20))");
                schema.execute("INSERT INTO public.shipments VALUES (501, 'unrelated')");
            }

            HikariConfig config = new HikariConfig();
            config.setDataSource(database);
            config.setMaximumPoolSize(2);
            config.setMinimumIdle(0);
            config.setConnectionTimeout(10000);
            config.setValidationTimeout(10000);
            config.setConnectionInitSql("SET SCHEMA fulfillment");

            try (HikariDataSource pool = new HikariDataSource(config);
                            Connection dispatchRequest = pool.getConnection()) {
                assertShipmentStatus(dispatchRequest, "ready");
                try (PreparedStatement dispatch = dispatchRequest.prepareStatement(
                                "UPDATE shipments SET status = ? WHERE shipment_id = ?")) {
                    dispatch.setString(1, "dispatched");
                    dispatch.setInt(2, 501);
                    assertEquals(1, dispatch.executeUpdate());
                }

                // The outstanding lease forces this request onto another physical connection.
                try (Connection trackingRequest = pool.getConnection()) {
                    assertShipmentStatus(trackingRequest, "dispatched");
                }
            }

            try (Statement audit = migration.createStatement();
                            ResultSet unrelated = audit.executeQuery(
                                            "SELECT status FROM public.shipments WHERE shipment_id = 501")) {
                assertTrue(unrelated.next());
                assertEquals("unrelated", unrelated.getString("status"),
                                "Application requests must not modify a same-named table in the public schema");
                assertFalse(unrelated.next());
            }
        }
    }

    private static void assertShipmentStatus(Connection connection, String expectedStatus) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                        "SELECT status FROM shipments WHERE shipment_id = ?")) {
            query.setInt(1, 501);
            try (ResultSet shipment = query.executeQuery()) {
                assertTrue(shipment.next());
                assertEquals(expectedStatus, shipment.getString("status"));
                assertFalse(shipment.next());
            }
        }
    }
}
