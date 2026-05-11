/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.velocity.runtime.RuntimeLogger;
import org.apache.velocity.runtime.parser.node.PropertyExecutor;
import org.apache.velocity.util.introspection.Introspector;
import org.junit.jupiter.api.Test;

public class PropertyExecutorTest {
    @Test
    void executeInvokesResolvedPropertyGetter() throws Exception {
        final PropertyExecutor executor = newExecutor("name");
        final Person person = new Person("Ada Lovelace");

        final Object result = executor.execute(person);

        assertThat(result).isEqualTo("Ada Lovelace");
        assertThat(executor.isAlive()).isTrue();
    }

    @Test
    void executeInvokesGetterThatMatchesPropertyCaseExactly() throws Exception {
        final PropertyExecutor executor = newExecutor("URL");
        final Person person = new Person("https://velocity.apache.org/");

        final Object result = executor.execute(person);

        assertThat(result).isEqualTo("https://velocity.apache.org/");
    }

    private static PropertyExecutor newExecutor(final String property) throws Exception {
        final NoOpRuntimeLogger logger = new NoOpRuntimeLogger();
        return new PropertyExecutor(logger, new Introspector(logger), Person.class, property);
    }

    public static final class Person {
        private final String value;

        Person(final String value) {
            this.value = value;
        }

        public String getName() {
            return value;
        }

        public String getURL() {
            return value;
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
