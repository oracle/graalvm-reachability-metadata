/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.util.introspection.SecureUberspector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class SecureIntrospectorImplTest {
    @Test
    void rendersAllowedMethodsAndBlocksRestrictedApplicationClass() throws Exception {
        VelocityEngine engine = VelocityTestSupport.configuredEngine();
        engine.setProperty("runtime.introspector.uberspect", SecureUberspector.class.getName());
        engine.setProperty("introspector.restrict.classes", RestrictedService.class.getName());
        engine.init();
        RestrictedService service = new RestrictedService();
        VelocityContext context = new VelocityContext();
        context.put("name", "Atlas");
        context.put("count", Integer.valueOf(12));
        context.put("enabled", Boolean.TRUE);
        context.put("service", service);
        assertEquals("ATLAS|12|true|", VelocityTestSupport.render(engine, context,
                "$name.toUpperCase()|$count.intValue()|$enabled.toString()|$!service.secret()"));
        assertFalse(service.wasCalled());
    }

    public static class RestrictedService {
        private boolean called;

        public String secret() {
            called = true;
            return "private";
        }

        public boolean wasCalled() {
            return called;
        }
    }
}
