/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import org.apache.velocity.VelocityContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MethodMapTest {
    @Test
    void selectsMostSpecificOverloadAndWidensPrimitiveArguments() throws Exception {
        VelocityContext context = new VelocityContext();
        context.put("formatter", new Formatter());
        context.put("count", Integer.valueOf(7));
        assertEquals("text:Atlas|number:7|14", VelocityTestSupport.render(context,
                "$formatter.describe('Atlas')|$formatter.describe($count)|$formatter.twice($count)"));
    }

    public static class Formatter {
        public String describe(Object value) {
            return "object:" + value;
        }

        public String describe(CharSequence value) {
            return "text:" + value;
        }

        public String describe(Number value) {
            return "number:" + value;
        }

        public long twice(long value) {
            return value * 2;
        }
    }
}
