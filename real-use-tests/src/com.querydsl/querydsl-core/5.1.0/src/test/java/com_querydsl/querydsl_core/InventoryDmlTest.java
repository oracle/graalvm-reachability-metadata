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
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.Configuration;
import com.querydsl.sql.H2Templates;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SQLQueryFactory;
import com.querydsl.sql.dml.SQLDeleteClause;
import com.querydsl.sql.dml.SQLInsertClause;
import com.querydsl.sql.dml.SQLUpdateClause;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises Querydsl's SQL DML subsystem to maintain an application's inventory.
public class InventoryDmlTest {
    @Test
    @Timeout(60)
    public void importsRestocksAndRemovesInventoryRows() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:querydsl_inventory_dml;DB_CLOSE_DELAY=-1", "sa", "")) {
            createInventoryTable(connection);

            Inventory inventory = new Inventory();
            Configuration configuration = new Configuration(new H2Templates());
            long inserted = new SQLInsertClause(connection, configuration, inventory)
                    .columns(inventory.sku, inventory.quantity, inventory.status)
                    .values("A-100", 4, "AVAILABLE")
                    .addBatch()
                    .values("B-200", 12, "AVAILABLE")
                    .addBatch()
                    .execute();
            assertEquals(2, inserted);

            long restocked = new SQLUpdateClause(connection, configuration, inventory)
                    .set(inventory.quantity, inventory.quantity.add(3))
                    .where(inventory.sku.eq("A-100"))
                    .execute();
            assertEquals(1, restocked);

            long removed = new SQLDeleteClause(connection, configuration, inventory)
                    .where(inventory.quantity.lt(10))
                    .execute();
            assertEquals(1, removed);

            List<Tuple> remainingItems = new SQLQueryFactory(configuration, () -> connection)
                    .select(inventory.sku, inventory.quantity, inventory.status)
                    .from(inventory)
                    .fetch();
            assertEquals(1, remainingItems.size());
            assertEquals("B-200", remainingItems.get(0).get(inventory.sku));
            assertEquals(12, remainingItems.get(0).get(inventory.quantity));
            assertEquals("AVAILABLE", remainingItems.get(0).get(inventory.status));
        }
    }

    private static void createInventoryTable(Connection connection) throws SQLException {
        connection.createStatement().executeUpdate("""
                CREATE TABLE inventory_items (
                    sku VARCHAR(20) PRIMARY KEY,
                    quantity INTEGER NOT NULL,
                    status VARCHAR(20) NOT NULL
                )
                """);
    }

    private static final class Inventory extends RelationalPathBase<InventoryRow> {
        private final StringPath sku = createString("sku");
        private final NumberPath<Integer> quantity = createNumber("quantity", Integer.class);
        private final StringPath status = createString("status");

        private Inventory() {
            super(InventoryRow.class, "inventory", null, "inventory_items");
        }
    }

    private static final class InventoryRow {
    }
}
