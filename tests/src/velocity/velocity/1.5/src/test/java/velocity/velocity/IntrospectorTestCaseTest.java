/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.util.Properties;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.log.NullLogChute;
import org.junit.jupiter.api.Test;

public class IntrospectorTestCaseTest {
    @Test
    void rendersTemplateUsingPrimitiveMethodIntrospection() throws Exception {
        final VelocityEngine engine = newVelocityEngine();
        final VelocityContext context = new VelocityContext();
        context.put("provider", new MethodProvider());
        final StringWriter writer = new StringWriter();

        final boolean rendered = engine.evaluate(
                context,
                writer,
                "primitive-introspection",
                "$provider.get(41) $provider.ready");

        assertThat(rendered).isTrue();
        assertThat(writer).hasToString("value=41 true");
    }

    private static VelocityEngine newVelocityEngine() throws Exception {
        final Properties properties = new Properties();
        properties.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, NullLogChute.class.getName());
        final VelocityEngine engine = new VelocityEngine();
        engine.init(properties);
        return engine;
    }

    public static final class MethodProvider {
        public String get(final int value) {
            return "value=" + value;
        }

        public boolean isReady() {
            return true;
        }
    }
}
