/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.runtime.RuntimeLogger;
import org.apache.velocity.runtime.parser.node.PropertyExecutor;
import org.apache.velocity.util.introspection.Introspector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class PropertyExecutorTest {
    @Test
    void invokesResolvedGetterThroughPropertyExecutor() throws Exception {
        VelocityContext context = new VelocityContext();
        context.put("message", "resolved through property executor");
        PropertyExecutor executor = createExecutor("keys");

        Object value = executor.execute(context);

        assertArrayEquals(new Object[] {"message" }, (Object[]) value);
    }

    private static PropertyExecutor createExecutor(String property) throws Exception {
        RuntimeLogger logger = new NoOpRuntimeLogger();
        Introspector introspector = new Introspector(logger);

        return new PropertyExecutor(logger, introspector, VelocityContext.class, property);
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
