/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import jakarta.el.ELProcessor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds a sales report with Jakarta Expression Language's stream and lambda subsystem.
 * A reporting application can keep its filtering and aggregation rules as expressions while
 * exposing its order data as ordinary application beans.
 */
public class OrderAnalyticsStreamExpressionTest {

    @Test
    @Timeout(60)
    public void aggregatesOrdersWithStreamExpressions() {
        ELProcessor expressions = new ELProcessor();
        expressions.defineBean("orders", List.of(
                new Order("ORD-100", "READY", "EU", 120),
                new Order("ORD-101", "HELD", "US", 240),
                new Order("ORD-102", "READY", "US", 85),
                new Order("ORD-103", "READY", "EU", 195)));
        expressions.setValue("minimumPriorityAmount", 150);

        Number readyRevenue = expressions.eval(
                "orders.stream().filter(order -> order.status == 'READY')"
                        + ".map(order -> order.amount).sum()");
        List<?> priorityOrders = expressions.eval(
                "orders.stream().filter(order -> order.amount >= minimumPriorityAmount)"
                        + ".map(order -> order.number).toList()");
        Number europeanOrderCount = expressions.eval(
                "orders.stream().filter(order -> order.region == 'EU').count()");
        Boolean hasLargeReadyOrder = expressions.eval(
                "orders.stream().filter(order -> order.status == 'READY')"
                        + ".anyMatch(order -> order.amount >= minimumPriorityAmount).orElse(false)");

        assertEquals(400, readyRevenue.intValue());
        assertEquals(List.of("ORD-101", "ORD-103"), priorityOrders);
        assertEquals(2, europeanOrderCount.intValue());
        assertTrue(hasLargeReadyOrder);
    }

    /** Order data exposed to the reporting expressions by the application. */
    public static final class Order {
        private final String number;
        private final String status;
        private final String region;
        private final int amount;

        public Order(String number, String status, String region, int amount) {
            this.number = number;
            this.status = status;
            this.region = region;
            this.amount = amount;
        }

        public String getNumber() {
            return number;
        }

        public String getStatus() {
            return status;
        }

        public String getRegion() {
            return region;
        }

        public int getAmount() {
            return amount;
        }
    }
}
