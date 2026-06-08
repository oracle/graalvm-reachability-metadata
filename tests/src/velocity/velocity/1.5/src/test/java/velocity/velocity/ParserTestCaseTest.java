/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.runtime.RuntimeConstants;
import org.junit.jupiter.api.Test;

public class ParserTestCaseTest {
    @Test
    public void evaluatesValidParserConstructsAndRejectsInvalidOnes() throws Exception {
        final VelocityEngine engine = newVelocityEngine();

        assertThat(evaluate(engine, "#if($a == $b)equals#end", Map.of("a", "same", "b", "same")))
                .isEqualTo("equals");
        assertThat(evaluate(engine, "#macro(foo)macro#end#foo()", Map.of()))
                .isEqualTo("macro");
        assertThat(evaluate(
                engine,
                "#foreach($i in $items)$i#end",
                Map.of("items", (Object) new String[] {"a", "b"})))
                .isEqualTo("ab");

        final ToStringCounter counter = new ToStringCounter();
        assertThat(evaluate(engine, "$counter", Map.of("counter", (Object) counter)))
                .isEqualTo("value");
        assertThat(counter.getTimesCalled()).isEqualTo(1);

        assertThatThrownBy(() -> evaluate(engine, "#if($a = $b)bad#end", Map.of()))
                .isInstanceOf(ParseErrorException.class);
        assertThatThrownBy(() -> evaluate(engine, "#macro($x)bad#end", Map.of()))
                .isInstanceOf(ParseErrorException.class);
        assertThatThrownBy(() -> evaluate(engine, "#macro(foo $a)$a#end#foo(value)", Map.of()))
                .isInstanceOf(ParseErrorException.class);
    }

    private static VelocityEngine newVelocityEngine() throws Exception {
        final Properties properties = new Properties();
        properties.setProperty(
                RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS,
                "org.apache.velocity.runtime.log.NullLogChute");

        final VelocityEngine engine = new VelocityEngine();
        engine.init(properties);
        return engine;
    }

    private static String evaluate(
            final VelocityEngine engine,
            final String template,
            final Map<String, ?> values) throws Exception {
        final VelocityContext context = new VelocityContext(new HashMap<>(values));
        final StringWriter writer = new StringWriter();

        engine.evaluate(context, writer, "parser-test", template);

        return writer.toString();
    }

    public static final class ToStringCounter {
        private int timesCalled;

        @Override
        public String toString() {
            timesCalled++;
            return "value";
        }

        private int getTimesCalled() {
            return timesCalled;
        }
    }
}
