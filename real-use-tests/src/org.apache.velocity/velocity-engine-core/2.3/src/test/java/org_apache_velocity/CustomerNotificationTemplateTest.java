/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.app.event.EventCartridge;
import org.apache.velocity.app.event.implement.EscapeHtmlReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Velocity's reference insertion event subsystem for safe HTML notifications.
public class CustomerNotificationTemplateTest {
    @Test
    @Timeout(60)
    public void escapesCustomerSuppliedTextWhileRenderingNotification() {
        VelocityEngine engine = new VelocityEngine();
        engine.init();

        VelocityContext context = new VelocityContext();
        context.put("customerName", "Maya <script>alert('x')</script>");
        context.put("orderNumber", "ORD-1042");
        EventCartridge eventCartridge = new EventCartridge();
        eventCartridge.addEventHandler(new EscapeHtmlReference());
        eventCartridge.attachToContext(context);

        StringWriter notification = new StringWriter();
        boolean rendered = engine.evaluate(
                context,
                notification,
                "customer-notification",
                "Hello $customerName, your order $orderNumber is ready.");

        assertEquals(true, rendered);
        assertEquals("Hello Maya &lt;script&gt;alert('x')&lt;/script&gt;, "
                + "your order ORD-1042 is ready.", notification.toString());
    }
}
