/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.log.NullLogSystem;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class RuntimeInstanceTest {
    @Test
    void initializesDefaultRuntimeComponentsAndRendersTemplate() throws Exception {
        VelocityEngine engine = new VelocityEngine();
        engine.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM, new NullLogSystem());
        engine.setProperty(RuntimeConstants.VM_LIBRARY, "");

        engine.init();

        VelocityContext context = new VelocityContext();
        context.put("items", Arrays.asList("first", "second"));
        context.put("message", "rendered");
        StringWriter writer = new StringWriter();

        boolean evaluated = engine.evaluate(
                context,
                writer,
                "runtime-instance-test",
                "#foreach($item in $items)$item#if($velocityCount < 2), #end#end: $message");

        assertThat(evaluated).isTrue();
        assertThat(writer.toString()).isEqualTo("first, second: rendered");
    }
}
