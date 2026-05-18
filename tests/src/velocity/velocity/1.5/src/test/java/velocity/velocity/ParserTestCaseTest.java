/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.Properties;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.log.NullLogChute;
import org.junit.jupiter.api.Test;

public class ParserTestCaseTest {
    @Test
    public void parsesForeachAndSetDirectives() throws Exception {
        final VelocityEngine engine = newVelocityEngine();
        final VelocityContext context = new VelocityContext();
        context.put("items", Arrays.asList("alpha", "beta", "gamma"));
        final StringWriter writer = new StringWriter();

        final boolean rendered = engine.evaluate(
                context,
                writer,
                "parser-regression",
                "#foreach($item in $items)$velocityCount:$item;#end#set($status = 'done')$status");

        assertThat(rendered).isTrue();
        assertThat(writer).hasToString("1:alpha;2:beta;3:gamma;done");
    }

    private static VelocityEngine newVelocityEngine() throws Exception {
        final Properties properties = new Properties();
        properties.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, NullLogChute.class.getName());
        final VelocityEngine engine = new VelocityEngine();
        engine.init(properties);
        return engine;
    }
}
