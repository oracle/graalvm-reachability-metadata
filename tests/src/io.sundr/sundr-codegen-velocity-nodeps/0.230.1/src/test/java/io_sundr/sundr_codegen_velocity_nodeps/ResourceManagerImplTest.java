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
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

public class ResourceManagerImplTest {
    @Test
    void exercisesPublicApi() throws Exception {
        VelocityEngine engine = TestSupport.classpathEngine();
        VelocityContext context = new VelocityContext();
        context.put("name", "Ada");
        StringWriter output = new StringWriter();
        engine.getTemplate("fixtures/greeting.vm", "UTF-8").merge(context, output);
        assertEquals("Hello Ada", output.toString());
        assertTrue(engine.resourceExists("fixtures/greeting.vm"));
    }
}
