/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity;

import java.io.StringWriter;
import java.util.List;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Velocity's dynamic template evaluation subsystem for stored order content.
public class DynamicContentTemplateTest {
    @Test
    @Timeout(60)
    public void rendersStoredContentInsideOrderTemplate() {
        VelocityEngine engine = new VelocityEngine();
        engine.init();

        VelocityContext context = new VelocityContext();
        context.put("orderNumber", "ORD-2048");
        context.put("customerName", "Maya");
        context.put("items", List.of("Notebook", "Pen"));
        context.put("storedContent", "Ship to $customerName\n"
                + "#foreach($item in $items)- $item\n#end");

        StringWriter order = new StringWriter();
        boolean rendered = engine.evaluate(
                context,
                order,
                "order-confirmation",
                "Order $orderNumber\n#evaluate($storedContent)");

        assertTrue(rendered);
        assertEquals("Order ORD-2048\nShip to Maya\n- Notebook\n- Pen\n", order.toString());
    }
}
