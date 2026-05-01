/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.runtime.RuntimeLogger;
import org.apache.velocity.runtime.parser.node.GetExecutor;
import org.apache.velocity.util.introspection.Introspector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GetExecutorTest {
    @Test
    void invokesVelocityContextGetThroughCurrentExecutor() throws Exception {
        VelocityContext context = new VelocityContext();
        context.put("message", "resolved through execute");
        GetExecutor executor = createExecutor("message");

        Object value = executor.execute(context);

        assertEquals("resolved through execute", value);
    }

    @Test
    void invokesVelocityContextGetThroughLegacyExecutor() throws Exception {
        VelocityContext context = new VelocityContext();
        context.put("message", "resolved through legacy execute");
        GetExecutor executor = createExecutor("message");

        Object value = executor.OLDexecute(context, null);

        assertEquals("resolved through legacy execute", value);
    }

    private static GetExecutor createExecutor(String key) throws Exception {
        RuntimeLogger logger = new NoOpRuntimeLogger();
        Introspector introspector = new Introspector(logger);

        return new GetExecutor(logger, introspector, VelocityContext.class, key);
    }

    private static class NoOpRuntimeLogger implements RuntimeLogger {
        @Override
        public void warn(Object message) {
        }

        @Override
        public void info(Object message) {
        }

        @Override
        public void error(Object message) {
        }

        @Override
        public void debug(Object message) {
        }
    }
}
