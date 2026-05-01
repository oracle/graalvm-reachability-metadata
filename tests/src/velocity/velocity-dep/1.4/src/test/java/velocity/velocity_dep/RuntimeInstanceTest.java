/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import java.io.StringReader;

import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.RuntimeInstance;
import org.apache.velocity.runtime.log.NullLogSystem;
import org.apache.velocity.runtime.parser.node.SimpleNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RuntimeInstanceTest {
    @Test
    void initializesRuntimeComponentsFromDefaultResources() throws Exception {
        RuntimeInstance runtime = newRuntimeInstance();

        assertEquals("ISO-8859-1", runtime.getString(RuntimeConstants.INPUT_ENCODING));
        assertNotNull(runtime.getUberspect());
    }

    @Test
    void parsesTemplateWithRuntimeLoadedDirectives() throws Exception {
        RuntimeInstance runtime = newRuntimeInstance();
        SimpleNode syntaxTree = runtime.parse(
                new StringReader("#foreach($name in $names)$name#end"),
                "runtime-instance-directives.vm");

        assertNotNull(syntaxTree);
    }

    private static RuntimeInstance newRuntimeInstance() throws Exception {
        RuntimeInstance runtime = new RuntimeInstance();
        runtime.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM, new NullLogSystem());

        runtime.init();
        return runtime;
    }
}
