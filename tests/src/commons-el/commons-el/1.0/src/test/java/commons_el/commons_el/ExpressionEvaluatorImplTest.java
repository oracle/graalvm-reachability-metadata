/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_el.commons_el;

import static org.assertj.core.api.Assertions.assertThat;

import javax.servlet.jsp.el.ELException;
import javax.servlet.jsp.el.VariableResolver;
import org.apache.commons.el.ExpressionEvaluatorImpl;
import org.junit.jupiter.api.Test;

public class ExpressionEvaluatorImplTest {
    @Test
    public void evaluatesStaticStringAsStringWithoutVariables() throws Exception {
        ExpressionEvaluatorImpl evaluator = new ExpressionEvaluatorImpl(true);
        VariableResolver resolver = new EmptyVariableResolver();

        Object result = evaluator.evaluate("static text", String.class, resolver, null);

        assertThat(result).isEqualTo("static text");
    }

    @Test
    public void evaluatesStaticStringAsObjectWithoutVariables() throws Exception {
        ExpressionEvaluatorImpl evaluator = new ExpressionEvaluatorImpl(true);
        VariableResolver resolver = new EmptyVariableResolver();

        Object result = evaluator.evaluate("another static text", Object.class, resolver, null);

        assertThat(result).isEqualTo("another static text");
    }

    private static final class EmptyVariableResolver implements VariableResolver {
        @Override
        public Object resolveVariable(String name) throws ELException {
            throw new ELException("Static evaluation must not resolve variables: " + name);
        }
    }
}
