/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;

import io.sundr.deps.org.apache.velocity.VelocityContext;
import org.junit.jupiter.api.Test;

public class UberspectImplInnerVelMethodImplTest {
    @Test
    void exercisesPublicApi() throws Exception {
        VelocityContext context = new VelocityContext();
        context.put("joiner", new Joiner());
        assertEquals("p:|p:a|p:a,b", TestSupport.render(context, "$joiner.join('p:')|$joiner.join('p:', 'a')|$joiner.join('p:', 'a', 'b')"));
    }

    public static class Joiner {
        public String join(String prefix, String... values) {
            return prefix + String.join(",", values);
        }
    }
}
