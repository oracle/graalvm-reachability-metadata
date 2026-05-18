/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.velocity.runtime.RuntimeLogger;
import org.apache.velocity.runtime.parser.node.PropertyExecutor;
import org.apache.velocity.util.introspection.Introspector;
import org.junit.jupiter.api.Test;

public class PropertyExecutorTest {
    @Test
    void executeInvokesResolvedPropertyGetter() throws Exception {
        final NoOpRuntimeLogger logger = new NoOpRuntimeLogger();
        final PropertyExecutor executor = new PropertyExecutor(logger, new Introspector(logger), Person.class,
                "name");

        final Object result = executor.execute(new Person("Ada"));

        assertThat(executor.isAlive()).isTrue();
        assertThat(result).isEqualTo("Ada");
    }

    public static final class Person {
        private final String name;

        Person(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private static final class NoOpRuntimeLogger implements RuntimeLogger {
        @Override
        public void warn(final Object message) {
        }

        @Override
        public void info(final Object message) {
        }

        @Override
        public void error(final Object message) {
        }

        @Override
        public void debug(final Object message) {
        }
    }
}
