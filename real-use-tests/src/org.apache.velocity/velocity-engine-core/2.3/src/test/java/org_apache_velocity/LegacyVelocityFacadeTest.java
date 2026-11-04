/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity;

import java.io.StringWriter;
import java.util.List;
import java.util.Properties;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Velocity's legacy static facade subsystem for rendering a shipping notice.
public class LegacyVelocityFacadeTest {
    @Test
    @Timeout(60)
    public void rendersShippingNoticeThroughConfiguredFacade() {
        Properties properties = new Properties();
        properties.setProperty("input.encoding", "UTF-8");
        Velocity.init(properties);
        try {
            VelocityContext context = new VelocityContext();
            context.put("customerName", "Maya");
            context.put("orderNumber", "ORD-11848");
            context.put("items", List.of("Notebook", "Pen"));

            StringWriter notice = new StringWriter();
            boolean rendered = Velocity.evaluate(
                    context,
                    notice,
                    "shipping-notice",
                    "Ship to $customerName\nOrder $orderNumber\n"
                            + "#foreach($item in $items)- $item\n#end");

            assertTrue(rendered);
            assertEquals("Ship to Maya\nOrder ORD-11848\n- Notebook\n- Pen\n",
                    notice.toString());
        } finally {
            Velocity.reset();
        }
    }
}
