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

public class MethodMapTest {
    @Test
    void exercisesPublicApi() throws Exception {
        VelocityContext context = new VelocityContext();
        context.put("formatter", new Formatter());
        assertEquals("text:word|number:3", TestSupport.render(context, "$formatter.describe('word')|$formatter.describe(3)"));
    }

    @Test
    void choosesTheMostSpecificApplicableOverload() {
        VelocityContext context = new VelocityContext();
        context.put("formatter", new SpecificFormatter());
        context.put("number", Integer.valueOf(7));
        assertEquals("text:word|object:7", TestSupport.render(context,
                "$formatter.describe('word')|$formatter.describe($number)"));
    }

    @Test
    void choosesCharSequenceOverObjectForMutableText() {
        VelocityContext context = new VelocityContext();
        context.put("formatter", new TextFormatter());
        context.put("text", new StringBuilder("draft"));
        assertEquals("text:draft", TestSupport.render(context, "$formatter.describe($text)"));
    }

    public static class TextFormatter {
        public String describe(Object value) {
            return "object:" + value;
        }

        public String describe(CharSequence value) {
            return "text:" + value;
        }
    }

    public static class SpecificFormatter {
        public String describe(Object value) {
            return "object:" + value;
        }

        public String describe(String value) {
            return "text:" + value;
        }
    }

    public static class Formatter {
        public String describe(String value) {
            return "text:" + value;
        }

        public String describe(Integer value) {
            return "number:" + value;
        }
    }
}
