/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity;

import java.io.StringWriter;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.FieldMethodizer;
import org.apache.velocity.app.VelocityEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Velocity's static field methodizer subsystem for shared order status labels.
public class OrderStatusLabelsTemplateTest {
    @Test
    @Timeout(60)
    public void rendersOrderStatusesFromApplicationConstants() {
        VelocityEngine engine = new VelocityEngine();
        engine.init();

        VelocityContext context = new VelocityContext();
        context.put("labels", new FieldMethodizer(OrderStatusLabels.class.getName()));
        context.put("orderNumber", "ORD-11848");
        context.put("status", "PAID");

        StringWriter page = new StringWriter();
        boolean rendered = engine.evaluate(
                context,
                page,
                "order-status",
                "Order $orderNumber: $status ($labels.PAID)");

        assertTrue(rendered);
        assertEquals("Order ORD-11848: PAID (Payment received)", page.toString());
    }

    public static final class OrderStatusLabels {
        public static final String PAID = "Payment received";
        public static final String PENDING = "Awaiting payment";

        private OrderStatusLabels() {
        }
    }
}
