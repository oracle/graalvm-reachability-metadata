/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi_jdbi3_core;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Jdbi's row-reduction subsystem for assembling order summaries from joined rows.
public class OrderSummaryReducerTest {
    @Test
    void assemblesEachOrderWithItsLineItems() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:order_summaries");

        jdbi.useHandle(handle -> {
            handle.execute("""
                    CREATE TABLE orders (
                        order_id VARCHAR(32) PRIMARY KEY,
                        customer VARCHAR(64) NOT NULL
                    )
                    """);
            handle.execute("""
                    CREATE TABLE order_line (
                        line_id VARCHAR(32) PRIMARY KEY,
                        order_id VARCHAR(32) NOT NULL,
                        product VARCHAR(64) NOT NULL,
                        quantity INT NOT NULL
                    )
                    """);
            handle.execute("""
                    INSERT INTO orders (order_id, customer)
                    VALUES ('ORD-1042', 'Acme'), ('ORD-1043', 'Globex')
                    """);
            handle.execute("""
                    INSERT INTO order_line (line_id, order_id, product, quantity)
                    VALUES
                        ('LINE-1', 'ORD-1042', 'keyboard', 2),
                        ('LINE-2', 'ORD-1042', 'mouse', 1),
                        ('LINE-3', 'ORD-1043', 'monitor', 3)
                    """);

            List<OrderSummary> summaries = handle.createQuery("""
                    SELECT o.order_id, o.customer, l.line_id, l.product, l.quantity
                    FROM orders o
                    JOIN order_line l ON l.order_id = o.order_id
                    ORDER BY o.order_id, l.line_id
                    """)
                    .reduceRows(new OrderSummaryReducer())
                    .toList();

            assertEquals(List.of(
                    new OrderSummary("ORD-1042", "Acme", List.of(
                            new LineItem("LINE-1", "keyboard", 2),
                            new LineItem("LINE-2", "mouse", 1))),
                    new OrderSummary("ORD-1043", "Globex", List.of(
                            new LineItem("LINE-3", "monitor", 3)))), summaries);
        });
    }

    private static final class OrderSummaryReducer
            implements LinkedHashMapRowReducer<String, OrderSummary> {
        @Override
        public void accumulate(Map<String, OrderSummary> container, RowView rowView) {
            String orderId = rowView.getColumn("order_id", String.class);
            OrderSummary order = container.computeIfAbsent(orderId,
                    id -> new OrderSummary(id, rowView.getColumn("customer", String.class),
                            new ArrayList<>()));
            order.lines().add(new LineItem(
                    rowView.getColumn("line_id", String.class),
                    rowView.getColumn("product", String.class),
                    rowView.getColumn("quantity", Integer.class)));
        }
    }

    private record LineItem(String id, String product, int quantity) {
    }

    private record OrderSummary(String id, String customer, List<LineItem> lines) {
    }
}
