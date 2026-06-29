/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_el.commons_el;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import javax.servlet.jsp.el.ELException;
import javax.servlet.jsp.el.VariableResolver;
import org.apache.commons.el.ExpressionEvaluatorImpl;
import org.junit.jupiter.api.Test;

public class ArraySuffixTest {
    @Test
    public void evaluatesBracketSyntaxAgainstJavaBeanProperty() throws Exception {
        ExpressionEvaluatorImpl evaluator = new ExpressionEvaluatorImpl(true);
        VariableResolver resolver = new MapBackedVariableResolver(Map.of("bean", new SampleBean("covered")));

        Object result = evaluator.evaluate("${bean['answer']}", String.class, resolver, null);

        assertThat(result).isEqualTo("covered");
    }

    private static final class MapBackedVariableResolver implements VariableResolver {
        private final Map<String, Object> variables;

        private MapBackedVariableResolver(Map<String, Object> variables) {
            this.variables = variables;
        }

        @Override
        public Object resolveVariable(String name) throws ELException {
            return variables.get(name);
        }
    }

    public static final class SampleBean {
        private final String answer;

        private SampleBean(String answer) {
            this.answer = answer;
        }

        public String getAnswer() {
            return answer;
        }
    }
}
