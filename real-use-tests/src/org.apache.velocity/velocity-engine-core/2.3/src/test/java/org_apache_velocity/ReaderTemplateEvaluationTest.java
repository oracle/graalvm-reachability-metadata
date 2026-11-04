/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity;

import java.io.StringReader;
import java.io.StringWriter;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Velocity's reader-based template evaluation subsystem for stored email content.
public class ReaderTemplateEvaluationTest {
    @Test
    @Timeout(60)
    public void rendersEmailTemplateLoadedFromReader() {
        VelocityEngine engine = new VelocityEngine();
        engine.init();

        VelocityContext context = new VelocityContext();
        context.put("recipient", "Maya");
        context.put("orderNumber", "ORD-11848");
        StringReader template = new StringReader(
                "Hello $recipient,\nYour order $orderNumber is ready for collection.");
        StringWriter email = new StringWriter();

        boolean rendered = engine.evaluate(context, email, "order-ready-email", template);

        assertTrue(rendered);
        assertEquals("Hello Maya,\nYour order ORD-11848 is ready for collection.",
                email.toString());
    }
}
