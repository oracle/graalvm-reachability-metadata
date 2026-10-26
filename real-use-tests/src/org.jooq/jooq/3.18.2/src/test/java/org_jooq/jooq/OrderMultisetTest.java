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
import org.jooq.Records;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises jOOQ's MULTISET nested-collection subsystem to load order summaries with their line items.
 */
public class OrderMultisetTest {
    private static final Table<Record> ORDERS = DSL.table(DSL.name("summary_order"));
    private static final Table<Record> LINES = DSL.table(DSL.name("summary_line"));
    private static final Field<Integer> ORDER_ID = DSL.field(DSL.name("order_id"), Integer.class);
    private static final Field<String> CUSTOMER = DSL.field(DSL.name("customer"), String.class);
    private static final Field<String> STATUS = DSL.field(DSL.name("status"), String.class);
    private static final Field<Integer> LINE_ORDER_ID = DSL.field(DSL.name("line_order_id"), Integer.class);
    private static final Field<Integer> LINE_NUMBER = DSL.field(DSL.name("line_number"), Integer.class);
    private static final Field<String> SKU = DSL.field(DSL.name("sku"), String.class);
    private static final Field<Integer> QUANTITY = DSL.field(DSL.name("quantity"), Integer.class);
    private static final Field<BigDecimal> UNIT_PRICE = DSL.field(DSL.name("unit_price"), BigDecimal.class);

    @Test
    @Timeout(60)
    public void loadsEachOpenOrderOnceWithOnlyItsOwnOrderedLines() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:order_multiset", "sa", "")) {
            DSLContext database = DSL.using(connection, SQLDialect.H2);
            database.createTable(ORDERS)
                    .column(ORDER_ID, SQLDataType.INTEGER.nullable(false))
                    .column(CUSTOMER, SQLDataType.VARCHAR(80).nullable(false))
                    .column(STATUS, SQLDataType.VARCHAR(20).nullable(false))
                    .primaryKey(ORDER_ID)
                    .execute();
            database.createTable(LINES)
                    .column(LINE_ORDER_ID, SQLDataType.INTEGER.nullable(false))
                    .column(LINE_NUMBER, SQLDataType.INTEGER.nullable(false))
                    .column(SKU, SQLDataType.VARCHAR(40).nullable(false))
                    .column(QUANTITY, SQLDataType.INTEGER.nullable(false))
                    .column(UNIT_PRICE, SQLDataType.DECIMAL(10, 2).nullable(false))
                    .primaryKey(LINE_ORDER_ID, LINE_NUMBER)
                    .constraint(DSL.foreignKey(LINE_ORDER_ID).references(ORDERS, ORDER_ID))
                    .execute();
            database.insertInto(ORDERS, ORDER_ID, CUSTOMER, STATUS)
                    .values(103, "Linus", "OPEN")
                    .values(101, "Ada", "OPEN")
                    .values(104, "Grace", "CANCELLED")
                    .values(102, "Ada", "OPEN")
                    .execute();
            database.insertInto(LINES, LINE_ORDER_ID, LINE_NUMBER, SKU, QUANTITY, UNIT_PRICE)
                    .values(101, 2, "pen", 3, new BigDecimal("1.25"))
                    .values(104, 1, "folder", 9, new BigDecimal("2.00"))
                    .values(103, 1, "pen", 1, new BigDecimal("1.25"))
                    .values(101, 1, "notebook", 2, new BigDecimal("4.50"))
                    .execute();

            // A correlated collection avoids duplicating an order for each of its lines.
            Field<List<OrderLine>> items = DSL.multiset(
                    DSL.select(SKU, QUANTITY, UNIT_PRICE)
                            .from(LINES)
                            .where(LINE_ORDER_ID.eq(ORDER_ID))
                            .orderBy(LINE_NUMBER))
                    .convertFrom(result -> result.map(Records.mapping(OrderLine::new)))
                    .as("items");
            List<OrderSummary> summaries = database.select(ORDER_ID, CUSTOMER, items)
                    .from(ORDERS)
                    .where(STATUS.eq("OPEN"))
                    .orderBy(ORDER_ID)
                    .fetch(Records.mapping(OrderSummary::new));

            // Empty orders remain visible, and another customer's lines cannot leak into them.
            assertEquals(List.of(
                    new OrderSummary(101, "Ada", List.of(
                            new OrderLine("notebook", 2, new BigDecimal("4.50")),
                            new OrderLine("pen", 3, new BigDecimal("1.25")))),
                    new OrderSummary(102, "Ada", List.of()),
                    new OrderSummary(103, "Linus", List.of(
                            new OrderLine("pen", 1, new BigDecimal("1.25"))))), summaries);
        }
    }

    private record OrderLine(String sku, int quantity, BigDecimal unitPrice) {
        private OrderLine {
            // Present currency consistently regardless of the SQL collection encoding's numeric scale.
            unitPrice = unitPrice.setScale(2);
        }
    }

    private record OrderSummary(int id, String customer, List<OrderLine> items) {
    }
}
