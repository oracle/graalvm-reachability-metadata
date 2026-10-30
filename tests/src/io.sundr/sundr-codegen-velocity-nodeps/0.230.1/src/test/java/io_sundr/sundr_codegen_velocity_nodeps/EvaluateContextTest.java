/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sundr.deps.org.apache.velocity.VelocityContext;
import io.sundr.deps.org.apache.velocity.app.VelocityEngine;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

public class EvaluateContextTest {
    @Test
    void exercisesPublicApi() throws Exception {
        VelocityContext context = new VelocityContext();
        context.put("name", "Ada");
        assertEquals("Hello Ada", TestSupport.render(context, "#evaluate('Hello $name')"));
    }

    @Test
    void rejectsAnEvaluateContextThatDoesNotImplementContext() {
        VelocityEngine engine = TestSupport.engine();
        engine.setProperty("directive.evaluate.context.class", StringBuilder.class.getName());
        engine.init();
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> engine.evaluate(new VelocityContext(), new StringWriter(), "invalid-context", "#evaluate('text')"));
        assertTrue(exception.getMessage().contains("does not implement"));
        assertTrue(exception.getMessage().contains("io.sundr.deps.org.apache.velocity.context.Context"));
    }
}
