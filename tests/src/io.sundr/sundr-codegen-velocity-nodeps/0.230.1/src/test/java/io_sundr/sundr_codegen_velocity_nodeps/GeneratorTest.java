/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;

import io.sundr.deps.org.apache.velocity.VelocityContext;
import io.sundr.deps.org.apache.velocity.texen.Generator;
import org.junit.jupiter.api.Test;

public class GeneratorTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Generator generator = Generator.getInstance();
        generator.setVelocityEngine(TestSupport.classpathEngine());
        VelocityContext context = new VelocityContext();
        context.put("name", "Ada");
        try {
            assertEquals("Hello Ada", generator.parse("fixtures/greeting.vm", context));
        } finally {
            generator.shutdown();
        }
    }
}
