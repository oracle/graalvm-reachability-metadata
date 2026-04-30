/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity_engine_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.util.StringJoiner;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.junit.jupiter.api.Test;

public class UberspectImplInnerVelMethodImplTest {
    @Test
    void invokesVarArgMethodWithNoSingleAndMultipleArguments() {
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.init();
        VelocityContext context = new VelocityContext();
        context.put("formatter", new VarArgFormatter());
        StringWriter writer = new StringWriter();

        boolean rendered = velocityEngine.evaluate(
                context,
                writer,
                "vararg-method-invocation",
                "$formatter.describe()|$formatter.describe('red')|$formatter.describe('red', 'blue', 'green')");

        assertThat(rendered).isTrue();
        assertThat(writer).hasToString("0:|1:red|3:red,blue,green");
    }

    public static class VarArgFormatter {
        public String describe(String... values) {
            StringJoiner joiner = new StringJoiner(",");
            for (String value : values) {
                joiner.add(value);
            }
            return values.length + ":" + joiner;
        }
    }
}
