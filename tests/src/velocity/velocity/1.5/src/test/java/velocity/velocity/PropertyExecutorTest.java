/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.velocity.runtime.log.Log;
import org.apache.velocity.runtime.log.NullLogChute;
import org.apache.velocity.runtime.parser.node.PropertyExecutor;
import org.apache.velocity.util.introspection.Introspector;
import org.junit.jupiter.api.Test;

public class PropertyExecutorTest {
    @Test
    void executeInvokesResolvedPropertyGetter() throws Exception {
        final Log log = new Log(new NullLogChute());
        final PropertyExecutor executor = new PropertyExecutor(
                log, new Introspector(log), Person.class, "name");

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
}
