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
import org.apache.velocity.app.event.InvalidReferenceEventHandler;
import org.apache.velocity.context.Context;
import org.apache.velocity.util.introspection.Info;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Velocity's invalid-reference event subsystem for optional customer profile fields.
public class OptionalCustomerProfileTest {
    @Test
    @Timeout(60)
    public void rendersFallbackForMissingOptionalProfileField() {
        VelocityEngine engine = new VelocityEngine();
        engine.init();

        VelocityContext context = new VelocityContext();
        context.put("customer", new Customer("Maya"));
        EventCartridge eventCartridge = new EventCartridge();
        eventCartridge.addInvalidReferenceEventHandler(new OptionalFieldHandler());
        assertTrue(eventCartridge.attachToContext(context));

        StringWriter profile = new StringWriter();
        boolean rendered = engine.evaluate(
                context,
                profile,
                "customer-profile",
                "Customer: $customer.name\nNickname: $customer.nickname");

        assertTrue(rendered);
        assertEquals("Customer: Maya\nNickname: not provided", profile.toString());
    }

    public static final class Customer {
        private final String name;

        public Customer(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static final class OptionalFieldHandler implements InvalidReferenceEventHandler {
        @Override
        public Object invalidGetMethod(Context context, String reference, Object object,
                String property, Info info) {
            if (object instanceof Customer && "nickname".equals(property)) {
                return "not provided";
            }
            return null;
        }

        @Override
        public boolean invalidSetMethod(Context context, String leftreference,
                String rightreference, Info info) {
            return false;
        }

        @Override
        public Object invalidMethod(Context context, String reference, Object object,
                String method, Info info) {
            return null;
        }
    }
}
