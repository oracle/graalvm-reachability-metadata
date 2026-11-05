/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb_hsqldb;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises HSQLDB's SQL trigger subsystem for recording shipment status changes.
public class ShipmentAuditTriggerTest {
    @Test
    @Timeout(60)
    public void recordsThePreviousStatusWhenAShipmentIsDispatched() throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setDatabase("mem:shipment_audit_trigger_scenario");
        dataSource.setUser("SA");
        dataSource.setPassword("");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE shipments ("
                    + "id INTEGER PRIMARY KEY, status VARCHAR(32))");
            statement.executeUpdate("CREATE TABLE shipment_audit ("
                    + "shipment_id INTEGER, previous_status VARCHAR(32), "
                    + "current_status VARCHAR(32))");
            statement.executeUpdate("""
                    CREATE TRIGGER shipment_status_audit
                    AFTER UPDATE OF status ON shipments
                    REFERENCING OLD ROW AS old_shipment NEW ROW AS new_shipment
                    FOR EACH ROW
                    BEGIN ATOMIC
                        INSERT INTO shipment_audit
                        VALUES (old_shipment.id, old_shipment.status, new_shipment.status);
                    END
                    """);
            statement.executeUpdate("INSERT INTO shipments VALUES (73, 'PACKED')");
        }

        try (Connection connection = dataSource.getConnection();
                PreparedStatement update = connection.prepareStatement(
                        "UPDATE shipments SET status = ? WHERE id = ?")) {
            update.setString(1, "DISPATCHED");
            update.setInt(2, 73);
            assertEquals(1, update.executeUpdate());
        }

        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT shipment_id, previous_status, current_status "
                                + "FROM shipment_audit WHERE shipment_id = ?")) {
            query.setInt(1, 73);
            try (ResultSet resultSet = query.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(73, resultSet.getInt("shipment_id"));
                assertEquals("PACKED", resultSet.getString("previous_status"));
                assertEquals("DISPATCHED", resultSet.getString("current_status"));
                assertFalse(resultSet.next());
            }
        }
    }
}
