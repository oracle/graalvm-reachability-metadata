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
import org.apache.velocity.app.event.EventCartridge;
import org.apache.velocity.app.event.MethodExceptionEventHandler;
import org.apache.velocity.context.Context;
import org.apache.velocity.util.introspection.Info;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Velocity's method exception event subsystem for resilient status pages.
public class MethodFailureRecoveryTest {
    @Test
    @Timeout(60)
    public void rendersFallbackWhenStatusServiceFails() {
        VelocityEngine engine = new VelocityEngine();
        engine.init();

        VelocityContext context = new VelocityContext();
        context.put("service", new StatusService());
        EventCartridge eventCartridge = new EventCartridge();
        eventCartridge.addMethodExceptionHandler(new StatusFailureHandler());
        assertTrue(eventCartridge.attachToContext(context));

        StringWriter page = new StringWriter();
        boolean rendered = engine.evaluate(
                context,
                page,
                "service-status",
                "Service status: $service.currentStatus()");

        assertTrue(rendered);
        assertEquals("Service status: temporarily unavailable", page.toString());
    }

    public static final class StatusService {
        public String currentStatus() {
            throw new IllegalStateException("status backend is unavailable");
        }
    }

    public static final class StatusFailureHandler implements MethodExceptionEventHandler {
        @Override
        public Object methodException(Context context, Class<?> referenceClass,
                String method, Exception exception, Info info) {
            return "temporarily unavailable";
        }
    }
}
