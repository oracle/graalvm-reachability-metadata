/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.Configuration;
import com.querydsl.sql.H2Templates;
import com.querydsl.sql.SQLQueryFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises Querydsl's dynamic path subsystem to filter shipments using fields selected by an application.
public class DynamicShipmentFilterTest {
    @Test
    @Timeout(60)
    public void filtersShipmentsUsingRuntimeSelectedColumns() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:querydsl_dynamic_shipments;DB_CLOSE_DELAY=-1", "sa", "")) {
            createShipmentsTable(connection);
            insertShipments(connection);

            PathBuilder<ShipmentRow> shipments = new PathBuilder<>(ShipmentRow.class, "shipments");
            StringPath warehouse = shipments.getString("warehouse");
            NumberPath<Integer> units = shipments.getNumber("units", Integer.class);
            String requestedWarehouse = "west";
            int minimumUnits = 10;

            List<Tuple> matchingShipments = new SQLQueryFactory(
                    new Configuration(new H2Templates()), () -> connection)
                    .select(warehouse, units)
                    .from(shipments)
                    .where(warehouse.eq(requestedWarehouse).and(units.goe(minimumUnits)))
                    .orderBy(units.desc())
                    .fetch();

            assertEquals(2, matchingShipments.size());
            assertEquals("west", matchingShipments.get(0).get(warehouse));
            assertEquals(24, matchingShipments.get(0).get(units));
            assertEquals("west", matchingShipments.get(1).get(warehouse));
            assertEquals(12, matchingShipments.get(1).get(units));
        }
    }

    private static void createShipmentsTable(Connection connection) throws SQLException {
        connection.createStatement().executeUpdate("""
                CREATE TABLE shipments (
                    id BIGINT PRIMARY KEY,
                    warehouse VARCHAR(20) NOT NULL,
                    units INTEGER NOT NULL
                )
                """);
    }

    private static void insertShipments(Connection connection) throws SQLException {
        connection.createStatement().executeUpdate("""
                INSERT INTO shipments (id, warehouse, units) VALUES
                    (1, 'west', 12),
                    (2, 'east', 30),
                    (3, 'west', 24),
                    (4, 'west', 5)
                """);
    }

    private static final class ShipmentRow {
    }
}
