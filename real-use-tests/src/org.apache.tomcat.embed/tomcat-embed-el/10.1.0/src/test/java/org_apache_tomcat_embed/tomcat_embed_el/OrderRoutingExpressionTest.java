/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import jakarta.el.ELProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Evaluates order-routing rules with Jakarta Expression Language beans and variables.
 * An order service delegates configurable routing and pricing expressions to Tomcat EL,
 * then uses the returned values to choose a fulfillment queue and customer-facing total.
 */
public class OrderRoutingExpressionTest {

    @Test
    @Timeout(60)
    public void routesAnOrderUsingBeanPropertiesAndApplicationVariables() {
        ELProcessor expressions = new ELProcessor();
        expressions.defineBean("order", new Order("order-11871", "EU", 3, 125));
        expressions.defineBean("shipping", new ShippingOptions(18, 2));
        expressions.setValue("discountPercent", 10);

        String queue = expressions.eval(
                "order.destination == 'EU' && order.itemCount >= shipping.priorityThreshold"
                        + " ? 'eu-priority' : 'standard'");
        Number merchandiseTotalExpression = expressions.eval("order.itemCount * order.unitPrice");
        int merchandiseTotal = merchandiseTotalExpression.intValue();
        expressions.setValue("merchandiseTotal", merchandiseTotal);
        Number discountedTotalExpression = expressions.eval(
                "merchandiseTotal * (100 - discountPercent) / 100");
        int discountedTotal = discountedTotalExpression.intValue();
        expressions.setValue("discountedTotal", discountedTotal);
        expressions.setValue("queue", queue);
        Number payableTotalExpression = expressions.eval("discountedTotal + shipping.fixedFee");
        int payableTotal = payableTotalExpression.intValue();
        expressions.setValue("payableTotal", payableTotal);
        String confirmation = expressions.eval(
                "order.id.concat(' routed to ').concat(queue).concat(' for ').concat(payableTotal)");

        assertEquals("eu-priority", queue);
        assertEquals(375, merchandiseTotal);
        assertEquals(337, discountedTotal);
        assertEquals(355, payableTotal);
        assertEquals("order-11871 routed to eu-priority for 355", confirmation);
    }

    /** Order details exposed to the expression engine by the fulfillment application. */
    public static final class Order {
        private final String id;
        private final String destination;
        private final int itemCount;
        private final int unitPrice;

        public Order(String id, String destination, int itemCount, int unitPrice) {
            this.id = id;
            this.destination = destination;
            this.itemCount = itemCount;
            this.unitPrice = unitPrice;
        }

        public String getId() {
            return id;
        }

        public String getDestination() {
            return destination;
        }

        public int getItemCount() {
            return itemCount;
        }

        public int getUnitPrice() {
            return unitPrice;
        }
    }

    /** Shipping configuration supplied by the application for the active region. */
    public static final class ShippingOptions {
        private final int fixedFee;
        private final int priorityThreshold;

        public ShippingOptions(int fixedFee, int priorityThreshold) {
            this.fixedFee = fixedFee;
            this.priorityThreshold = priorityThreshold;
        }

        public int getFixedFee() {
            return fixedFee;
        }

        public int getPriorityThreshold() {
            return priorityThreshold;
        }
    }
}
