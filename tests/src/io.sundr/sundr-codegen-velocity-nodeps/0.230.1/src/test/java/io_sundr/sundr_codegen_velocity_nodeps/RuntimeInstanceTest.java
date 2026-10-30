/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sundr.deps.org.apache.velocity.VelocityContext;
import io.sundr.deps.org.apache.velocity.app.VelocityEngine;
import io.sundr.deps.org.apache.velocity.app.event.ReferenceInsertionEventHandler;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

public class RuntimeInstanceTest {
    @Test
    void exercisesPublicApi() throws Exception {
        VelocityContext context = new VelocityContext();
        context.put("name", "World");
        assertEquals("Hello World", TestSupport.render(context, "Hello $name"));
    }

    @Test
    void loadsConfiguredReferenceInsertionHandler() {
        VelocityEngine engine = TestSupport.engine();
        engine.setProperty("eventhandler.referenceinsertion.class", BracketReferenceHandler.class.getName());
        engine.init();
        VelocityContext context = new VelocityContext();
        context.put("text", "content");
        StringWriter output = new StringWriter();
        assertTrue(engine.evaluate(context, output, "escaped-reference", "$text"));
        assertEquals("[content]", output.toString());
    }

    public static class BracketReferenceHandler implements ReferenceInsertionEventHandler {
        @Override
        public Object referenceInsert(String reference, Object value) {
            return "[" + value + "]";
        }
    }
}
