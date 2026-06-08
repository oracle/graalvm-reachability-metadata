/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.log.NullLogSystem;
import org.junit.jupiter.api.Test;

public class SetPropertyExecutorTest {
    @Test
    void setsBeanPropertyFromTemplatePropertyAssignment() throws Exception {
        final VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM, new NullLogSystem());
        velocityEngine.init();

        final Person person = new Person();
        person.setName("old-value");
        final VelocityContext context = new VelocityContext();
        context.put("person", person);

        final StringWriter writer = new StringWriter();
        final boolean evaluated = velocityEngine.evaluate(
                context,
                writer,
                "SetPropertyExecutorTest",
                "#set($person.name = 'Grace')$person.name");

        assertThat(evaluated).isTrue();
        assertThat(writer).hasToString("Grace");
        assertThat(person.getName()).isEqualTo("Grace");
    }

    public static final class Person {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }
    }
}
