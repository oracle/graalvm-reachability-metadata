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

public class PutExecutorTest {
    @Test
    void exercisesPublicApi() throws Exception {
        VelocityContext context = new VelocityContext();
        context.put("data", new Lookup());
        assertEquals("Grace", TestSupport.render(context, "#set($data.name = 'Grace')$data.name"));
    }

    public static class Lookup {
        private String value = "Ada";

        public String get(String key) {
            return value;
        }

        public Object put(String key, Object value) {
            String previous = this.value;
            this.value = (String) value;
            return previous;
        }
    }
}
