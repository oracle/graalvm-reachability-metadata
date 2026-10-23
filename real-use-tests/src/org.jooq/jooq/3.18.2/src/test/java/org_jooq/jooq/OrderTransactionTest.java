/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jooq.jooq;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises jOOQ's JDBC transaction subsystem for atomic order placement and inventory reservation.
 */
public class OrderTransactionTest {
    private static final Table<Record> INVENTORY = DSL.table(DSL.name("inventory"));
    private static final Table<Record> ORDERS = DSL.table(DSL.name("purchase_order"));
    private static final Field<String> SKU = DSL.field(DSL.name("sku"), String.class);
    private static final Field<Integer> AVAILABLE = DSL.field(DSL.name("available"), Integer.class);
    private static final Field<Integer> ORDER_ID = DSL.field(DSL.name("order_id"), Integer.class);
    private static final Field<String> CUSTOMER = DSL.field(DSL.name("customer"), String.class);

    @Test
    @Timeout(60)
    public void commitsCompleteOrdersAndRollsBackPartialReservations() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:order_transactions", "sa", "")) {
            DSLContext database = DSL.using(connection, SQLDialect.H2);
            database.createTable(INVENTORY)
                    .column(SKU, SQLDataType.VARCHAR(40).nullable(false))
                    .column(AVAILABLE, SQLDataType.INTEGER.nullable(false))
                    .primaryKey(SKU)
                    .execute();
            database.createTable(ORDERS)
                    .column(ORDER_ID, SQLDataType.INTEGER.nullable(false))
                    .column(CUSTOMER, SQLDataType.VARCHAR(80).nullable(false))
                    .primaryKey(ORDER_ID)
                    .execute();
            database.insertInto(INVENTORY, SKU, AVAILABLE)
                    .values("notebook", 10)
                    .values("pen", 4)
                    .execute();

            placeOrder(database, 101, "Ada", List.of(new Line("notebook", 2), new Line("pen", 1)));

            assertEquals(Map.of("notebook", 8, "pen", 3), stock(database));
            assertEquals("Ada", database.select(CUSTOMER).from(ORDERS)
                    .where(ORDER_ID.eq(101)).fetchSingle(CUSTOMER));

            // The first reservation succeeds; the second rejects the whole order.
            InsufficientStockException rejection = assertThrows(InsufficientStockException.class,
                    () -> placeOrder(database, 102, "Grace",
                            List.of(new Line("notebook", 3), new Line("pen", 5))));
            assertEquals("Insufficient stock: pen", rejection.getMessage());
            assertEquals(Map.of("notebook", 8, "pen", 3), stock(database));
            assertEquals(List.of(101), database.select(ORDER_ID).from(ORDERS)
                    .orderBy(ORDER_ID).fetch(ORDER_ID));

            // A later order can still commit on the same connection after the rollback.
            placeOrder(database, 103, "Linus", List.of(new Line("pen", 3)));
            assertEquals(Map.of("notebook", 8, "pen", 0), stock(database));
            assertEquals(List.of("Ada", "Linus"), database.select(CUSTOMER).from(ORDERS)
                    .orderBy(ORDER_ID).fetch(CUSTOMER));
        }
    }

    private static void placeOrder(DSLContext database, int orderId, String customer, List<Line> lines) {
        database.transaction(configuration -> {
            DSLContext transaction = DSL.using(configuration);
            transaction.insertInto(ORDERS, ORDER_ID, CUSTOMER).values(orderId, customer).execute();
            for (Line line : lines) {
                int reserved = transaction.update(INVENTORY)
                        .set(AVAILABLE, AVAILABLE.sub(line.quantity()))
                        .where(SKU.eq(line.sku()).and(AVAILABLE.ge(line.quantity())))
                        .execute();
                if (reserved != 1) {
                    throw new InsufficientStockException(line.sku());
                }
            }
        });
    }

    private static Map<String, Integer> stock(DSLContext database) {
        return database.select(SKU, AVAILABLE).from(INVENTORY).fetchMap(SKU, AVAILABLE);
    }

    private record Line(String sku, int quantity) {
    }

    private static final class InsufficientStockException extends RuntimeException {
        private InsufficientStockException(String sku) {
            super("Insufficient stock: " + sku);
        }
    }
}
