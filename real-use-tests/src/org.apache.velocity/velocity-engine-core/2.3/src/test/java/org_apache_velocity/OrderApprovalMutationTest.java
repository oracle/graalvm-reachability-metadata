/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity;

import java.io.StringWriter;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Velocity's bean property assignment subsystem for approving an order.
public class OrderApprovalMutationTest {
    @Test
    @Timeout(60)
    public void updatesOrderThroughTemplatePropertyAssignment() {
        VelocityEngine engine = new VelocityEngine();
        engine.init();

        Order order = new Order("ORD-11848", "pending");
        VelocityContext context = new VelocityContext();
        context.put("order", order);

        StringWriter summary = new StringWriter();
        boolean rendered = engine.evaluate(
                context,
                summary,
                "order-approval",
                "#set($order.status = 'approved')Order $order.number: $order.status");

        assertTrue(rendered);
        assertEquals("Order ORD-11848: approved", summary.toString());
        assertEquals("approved", order.getStatus());
    }

    public static final class Order {
        private final String number;
        private String status;

        public Order(String number, String status) {
            this.number = number;
            this.status = status;
        }

        public String getNumber() {
            return number;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
