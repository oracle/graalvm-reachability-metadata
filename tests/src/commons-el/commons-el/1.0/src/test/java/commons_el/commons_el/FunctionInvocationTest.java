/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_el.commons_el;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import javax.servlet.jsp.el.ELException;
import javax.servlet.jsp.el.FunctionMapper;
import javax.servlet.jsp.el.VariableResolver;
import org.apache.commons.el.ExpressionEvaluatorImpl;
import org.apache.commons.el.StringLiteral;
import org.junit.jupiter.api.Test;

public class FunctionInvocationTest {
    @Test
    public void evaluatesMappedStaticFunctionWithLiteralArgument() throws Exception {
        ExpressionEvaluatorImpl evaluator = new ExpressionEvaluatorImpl(true);
        FunctionMapper functionMapper = new StringLiteralFunctionMapper();

        Object result = evaluator.evaluate(
                "${strings:quote(\"green\")}",
                String.class,
                new EmptyVariableResolver(),
                functionMapper);

        assertThat(result).isEqualTo("\"green\"");
    }

    private static final class StringLiteralFunctionMapper implements FunctionMapper {
        private final Method toStringTokenMethod;

        private StringLiteralFunctionMapper() throws NoSuchMethodException {
            toStringTokenMethod = StringLiteral.class.getMethod("toStringToken", String.class);
        }

        @Override
        public Method resolveFunction(String prefix, String localName) {
            if ("strings".equals(prefix) && "quote".equals(localName)) {
                return toStringTokenMethod;
            }
            return null;
        }
    }

    private static final class EmptyVariableResolver implements VariableResolver {
        @Override
        public Object resolveVariable(String name) throws ELException {
            throw new ELException("Function evaluation must not resolve variables: " + name);
        }
    }
}
